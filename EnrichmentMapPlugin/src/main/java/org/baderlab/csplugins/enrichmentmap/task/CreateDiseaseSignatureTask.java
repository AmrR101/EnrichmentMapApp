/*
 *                       EnrichmentMap Cytoscape Plugin
 *
 * Copyright (c) 2008-2009 Bader Lab, Donnelly Centre for Cellular and Biomolecular 
 * Research, University of Toronto
 *
 * Contact: http://www.baderlab.org
 *
 * Code written by: Ruth Isserlin
 * Authors: Daniele Merico, Ruth Isserlin, Oliver Stueker, Gary D. Bader
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY, WITHOUT EVEN THE IMPLIED WARRANTY OF
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE.  The software and
 * documentation provided hereunder is on an "as is" basis, and
 * University of Toronto
 * has no obligations to provide maintenance, support, updates, 
 * enhancements or modifications.  In no event shall the
 * University of Toronto
 * be liable to any party for direct, indirect, special,
 * incidental or consequential damages, including lost profits, arising
 * out of the use of this software and its documentation, even if
 * University of Toronto
 * has been advised of the possibility of such damage.  
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.
 *
 */

// $Id$
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
// $HeadURL$

package org.baderlab.csplugins.enrichmentmap.task;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.baderlab.csplugins.enrichmentmap.model.DataSet;
import org.baderlab.csplugins.enrichmentmap.model.EnrichmentMap;
import org.baderlab.csplugins.enrichmentmap.model.EnrichmentMapManager;
import org.baderlab.csplugins.enrichmentmap.model.GeneSet;
import org.baderlab.csplugins.enrichmentmap.model.GenesetSimilarity;
import org.baderlab.csplugins.enrichmentmap.model.PostAnalysisFilterParameters;
import org.baderlab.csplugins.enrichmentmap.model.PostAnalysisFilterType;
import org.baderlab.csplugins.enrichmentmap.model.PostAnalysisParameters;
import org.baderlab.csplugins.enrichmentmap.model.Ranking;
import org.baderlab.csplugins.enrichmentmap.style.EMStyleBuilder.Columns;
import org.baderlab.csplugins.enrichmentmap.style.WidthFunction;
import org.baderlab.csplugins.enrichmentmap.util.NetworkUtil;
import org.baderlab.csplugins.mannwhit.MannWhitneyUTestSided;
import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.event.CyEventHelper;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.View;
import org.cytoscape.view.presentation.property.BasicVisualLexicon;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.ObservableTask;
import org.cytoscape.work.TaskMonitor;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

/**
 * Cytoscape-Task to perform Disease-Signature Post-Analysis
 */
public class CreateDiseaseSignatureTask extends AbstractTask implements ObservableTask {
	
	@Inject private CyApplicationManager applicationManager;
	@Inject private CyEventHelper eventHelper;
	@Inject private EnrichmentMapManager emManager;
	@Inject private Provider<WidthFunction> widthFunctionProvider;

	private PostAnalysisParameters paParams;
	private final EnrichmentMap map;
	private final String interaction;

	private Map<String, GeneSet> EnrichmentGenesets;
	private Map<String, GeneSet> SignatureGenesets;
	private Map<String, GeneSet> SelectedSignatureGenesets;

	private double currentNodeY_offset;
	
	// Gene Populations:
	private Set<Integer> EnrichmentGenes;
	private Set<Integer> SignatureGenes;

	// Ranks
	private Ranking ranks;
	private Map<String, GenesetSimilarity> geneset_similarities;

	private CreateDiseaseSignatureTaskResult.Builder taskResult = new CreateDiseaseSignatureTaskResult.Builder();

	
	public interface Factory {
		CreateDiseaseSignatureTask create(EnrichmentMap map, PostAnalysisParameters paParams);
	}
	
	@Inject
	public CreateDiseaseSignatureTask(@Assisted EnrichmentMap map, @Assisted PostAnalysisParameters paParams) {
		this.map = map;
		this.paParams = paParams; 
		
		DataSet dataset = map.getDataset(paParams.getSignatureDataSet());
		this.ranks = dataset.getExpressionSets().getRanks().get(paParams.getSignatureRankFile());


		// we want genesets of interest that are not signature genesets put there by previous runs of post-analysis
		this.EnrichmentGenesets = new HashMap<>();
		for(Map.Entry<String, GeneSet> gs : dataset.getGenesetsOfInterest().getGenesets().entrySet()) {
			if(map.getEnrichmentGenesets().containsKey(gs.getKey())) {
				this.EnrichmentGenesets.put(gs.getKey(), gs.getValue());
			}
		}
		this.SignatureGenesets = this.paParams.getSignatureGenesets().getGenesets();
		this.geneset_similarities = new HashMap<>();

		this.SelectedSignatureGenesets = new HashMap<String, GeneSet>();
		for(String geneset : paParams.getSelectedSignatureSetNames()) {
			this.SelectedSignatureGenesets.put(geneset, this.SignatureGenesets.get(geneset));
		}

		// EnrichmentGenes: pool of all genes in Enrichment Gene Sets
		//TODO: get enrichment map genes from enrichment map parameters now that they are computed there.
		EnrichmentGenes = new HashSet<>();
		for(GeneSet geneSet : EnrichmentGenesets.values()) {
			EnrichmentGenes.addAll(geneSet.getGenes());
		}
		// SignatureGenes: pool of all genes in Signature Gene Sets
		SignatureGenes = new HashSet<>();
		for(GeneSet geneSet : SignatureGenesets.values()) {
			SignatureGenes.addAll(geneSet.getGenes());
		}

		this.interaction = getInteraction();
	}

	private String getInteraction() {
		return PostAnalysisParameters.SIGNATURE_INTERACTION_TYPE;
	}

	public void buildDiseaseSignature(TaskMonitor taskMonitor) {
		// Calculate Similarity between Signature Gene Sets * and Enrichment Genesets.
		int maxValue = SelectedSignatureGenesets.size() * EnrichmentGenesets.size();
		if(taskMonitor != null)
			taskMonitor.setStatusMessage("Computing Geneset similarity - " + maxValue + " rows");
		int currentProgress = 0;

		double currentNodeY_increment = 150.0;

		try {
			CyNetwork currentNetwork  = applicationManager.getCurrentNetwork();
			CyNetworkView currentView = applicationManager.getCurrentNetworkView();
			taskResult.setNetwork(currentNetwork);
			taskResult.setNetworkView(currentView);

			String prefix = paParams.getAttributePrefix();
			if(prefix == null) {
				prefix = "EM1_";
				paParams = PostAnalysisParameters.Builder.from(paParams).setAttributePrefix(prefix).build();
			}

			//get the node attribute and edge attribute tables
			CyTable cyEdgeAttrs = createEdgeAttributes(currentNetwork, "", prefix);
			CyTable cyNodeAttrs = createNodeAttributes(currentNetwork, "", prefix);

			// make a HashMap of all Nodes in the Network
			Map<String, CyNode> nodesMap = createNodeMap(currentNetwork, cyNodeAttrs, prefix);

			// Common gene universe: Intersection of EnrichmentGenes and SignatureGenes
			Set<Integer> geneUniverse = ImmutableSet.copyOf(EnrichmentGenes);

			Map<String, String> duplicateGenesets = new HashMap<>();

			//iterate over selected Signature genesets
			for(String hub_name : SelectedSignatureGenesets.keySet()) {

				// get the Signature Genes, restrict them to the Gene-Universe and add them to the Parameters
				GeneSet sigGeneSet = SelectedSignatureGenesets.get(hub_name);

				// Check to see if the signature geneset shares the same name with an 
				// enrichment geneset. If it does, give the signature geneset a unique name
				if(EnrichmentGenesets.containsKey(hub_name)) {
					duplicateGenesets.put(hub_name, "PA_" + hub_name);
					hub_name = "PA_" + hub_name;
				}

				//the signature genes in this signature gene set 
				Set<Integer> sigGenes = sigGeneSet.getGenes();

				// the genes that are in this signature gene set as well as in the Universe of Enrichment-GMT Genes.    
				Set<Integer> sigGenesInUniverse = Sets.intersection(sigGenes, geneUniverse);

				emManager.getEnrichmentMap(currentNetwork.getSUID()).getSignatureGenesets().put(hub_name, sigGeneSet);

				// iterate over Enrichment Genesets
				for(String geneset_name : EnrichmentGenesets.keySet()) {

					// Calculate Percentage.  This must be a value between 0..100.
					int percentComplete = (int) (((double) currentProgress / maxValue) * 100);
					// Estimate Time Remaining
					if(taskMonitor != null) {
						taskMonitor.setProgress(percentComplete);
						taskMonitor.setTitle("Post Analysis");
					}
					currentProgress++;

					if(cancelled) {
						throw new InterruptedException();
					}

					// Calculate overlap:

					//Check to see if this comparison has been done
					//The key for the set of geneset similarities is the combination of the two names.  Check for either variation name1_name2 or name2_name1
					String similarity_key1 = hub_name + " (" + interaction + ") " + geneset_name;
					//String similarity_key2 = geneset_name + " (" + interaction + ") " + hub_name;

					//first check to see if the terms are the same
					if(hub_name.equalsIgnoreCase(geneset_name)) {
						//don't compare two identical genesets
					} else if(!nodesMap.containsKey(geneset_name)) {
						// skip if the Geneset is not in the Network
					} else if(Columns.NODE_GS_TYPE.get(cyNodeAttrs.getRow(nodesMap.get(geneset_name).getSUID()), prefix, null)
							.equalsIgnoreCase(Columns.NODE_GS_TYPE_SIGNATURE)) {
						// skip if the Geneset is a Signature Node from a previous analysis
					}
					/*
					 * else if(geneset_similarities.containsKey(similarity_key1)
					 * || geneset_similarities.containsKey(similarity_key2)){
					 * //skip this geneset comparison. It has already been done.
					 * }
					 */
					else {
						//get the Enrichment geneset
						GeneSet enrGeneset = EnrichmentGenesets.get(geneset_name);
						
						// restrict to a common gene universe
						Set<Integer> enrGenes = Sets.intersection(enrGeneset.getGenes(), geneUniverse);
						Set<Integer> union = Sets.union(sigGenes, enrGenes);
						Set<Integer> intersection = Sets.intersection(sigGenesInUniverse, enrGenes);

						// Only calculate Mann-Whitney pValue if there is overlap
						if(intersection.size() > 0) {
							double coeffecient = ComputeSimilarityTaskParallel.computeSimilarityCoeffecient(map.getParams(), intersection, union, sigGenes, enrGenes);
							GenesetSimilarity comparison = new GenesetSimilarity(hub_name, geneset_name, coeffecient, interaction, intersection);

							PostAnalysisFilterType filterType = paParams.getRankTestParameters().getType();
							switch(filterType) {
								case HYPERGEOM:
									int universeSize1 = paParams.getUniverseSize();
									hypergeometric(universeSize1, sigGenesInUniverse, enrGenes, intersection, comparison);
									break;
								case MANN_WHIT_TWO_SIDED:
								case MANN_WHIT_GREATER:
								case MANN_WHIT_LESS:
									mannWhitney(intersection, comparison);
								default: // want mann-whit to fall through
									int universeSize2 = map.getNumberOfGenes(); // #70 calculate hypergeometric also
									hypergeometric(universeSize2, sigGenesInUniverse, enrGenes, intersection, comparison);
									break;
							}

							geneset_similarities.put(similarity_key1, comparison);
						}
					}
				} // End: iterate over Enrichment Genesets

				// Create Signature Hub Node
				boolean created = createHubNode(hub_name, currentNetwork, currentView, currentNodeY_offset, prefix, cyEdgeAttrs, cyNodeAttrs, geneUniverse, sigGeneSet);
				if(created)
					currentNodeY_offset += currentNodeY_increment;

			} // End: iterate over Signature Genesets

			// Update signature geneset map with new names of all signature genesets that have duplicates
			for(String original_hub_name : duplicateGenesets.keySet()) {
				GeneSet geneset = SelectedSignatureGenesets.remove(original_hub_name);
				SelectedSignatureGenesets.put(duplicateGenesets.get(original_hub_name), geneset);
			}
			duplicateGenesets.clear();

			// Create Signature Hub Edges
			for(String edge_name : geneset_similarities.keySet()) {
				if(cancelled)
					throw new InterruptedException();

				if(!geneset_similarities.get(edge_name).getInteractionType().equals(interaction))
					// skip if it's not a signature edge from the same dataset
					continue;
				if(!(this.SelectedSignatureGenesets.containsKey(geneset_similarities.get(edge_name).getGeneset1_Name())
						|| this.SelectedSignatureGenesets.containsKey(geneset_similarities.get(edge_name).getGeneset2_Name())))
					// skip if not either of the adjacent nodes is a SelectedSignatureGenesets of the current analysis (fixes Bug #44)
					continue;

				boolean passed_cutoff = passesCutoff(edge_name);
				createEdge(edge_name, currentNetwork, currentView, prefix, cyEdgeAttrs, cyNodeAttrs, passed_cutoff);

			}

			widthFunctionProvider.get().setEdgeWidths(currentNetwork, prefix, taskMonitor);
		} catch(InterruptedException e) {
			// TODO cancel task
		}
	}

	private boolean passesCutoff(String edge_name) {
		GenesetSimilarity similarity = geneset_similarities.get(edge_name);
		PostAnalysisFilterParameters filterParams = paParams.getRankTestParameters();

		switch(filterParams.getType()) {
			case HYPERGEOM:
				return similarity.getHypergeom_pvalue() <= filterParams.getValue();
			case MANN_WHIT_TWO_SIDED:
				return !similarity.isMannWhitMissingRanks() && similarity.getMann_Whit_pValue_twoSided() <= filterParams.getValue();
			case MANN_WHIT_GREATER:
				return !similarity.isMannWhitMissingRanks() && similarity.getMann_Whit_pValue_greater() <= filterParams.getValue();
			case MANN_WHIT_LESS:
				return !similarity.isMannWhitMissingRanks() && similarity.getMann_Whit_pValue_less() <= filterParams.getValue();
			case NUMBER:
				return similarity.getSizeOfOverlap() >= filterParams.getValue();
			case PERCENT:
				String geneset_name = similarity.getGeneset2_Name();
				GeneSet enrGeneset = EnrichmentGenesets.get(geneset_name);
				int enrGenesetSize = enrGeneset.getGenes().size();
				double relative_per = (double) similarity.getSizeOfOverlap() / (double) enrGenesetSize;
				return relative_per >= filterParams.getValue() / 100.0;
			case SPECIFIC:
				String hub_name = similarity.getGeneset1_Name();
				GeneSet sigGeneSet = SelectedSignatureGenesets.get(hub_name);
				int sigGeneSetSize = sigGeneSet.getGenes().size();
				double relative_per2 = (double) similarity.getSizeOfOverlap() / (double) sigGeneSetSize;
				return relative_per2 >= filterParams.getValue() / 100.0;
			default:
				return false;
		}
	}

	/**
	 * Returns true if a hub-node was actually created, false if the existing
	 * one was reused.
	 */
	private boolean createHubNode(String hub_name, CyNetwork current_network, CyNetworkView current_view,
			double currentNodeY_offset, String prefix, CyTable cyEdgeAttrs, CyTable cyNodeAttrs,
			Set<Integer> geneUniverse, GeneSet sigGeneSet) {

		boolean created = false;
		// test for existing node first
		CyNode hub_node = NetworkUtil.getNodeWithValue(current_network, cyNodeAttrs, CyNetwork.NAME, hub_name);
		if(hub_node == null) {
			hub_node = current_network.addNode();
			taskResult.addNewNode(hub_node);
			created = true;
		}

		current_network.getRow(hub_node).set(CyNetwork.NAME, hub_name);
		//flush events to make sure view has been created.
		this.eventHelper.flushPayloadEvents();

		// add currentNodeY_offset to initial Y position of the Node
		// and increase currentNodeY_offset for the next Node
		View<CyNode> hubNodeView = current_view.getNodeView(hub_node);
		double hubNodeY = hubNodeView.getVisualProperty(BasicVisualLexicon.NODE_Y_LOCATION);
		if(created) { // don't move nodes that already exist
			hubNodeView.setVisualProperty(BasicVisualLexicon.NODE_Y_LOCATION, hubNodeY + currentNodeY_offset);
		}

		String formatted_label = CreateEMNetworkTask.formatLabel(hub_name);
		CyRow row = cyNodeAttrs.getRow(hub_node.getSUID());
		Columns.NODE_FORMATTED_NAME.set(row, prefix, null, formatted_label);

		List<String> gene_list = sigGeneSet.getGenes().stream()
				.map(map::getGeneFromHashKey)
				.filter(Objects::nonNull)
				.sorted()
				.collect(Collectors.toList());
		
		List<String> enr_gene_list = sigGeneSet.getGenes().stream()
				.filter(geneUniverse::contains)
				.map(map::getGeneFromHashKey)
				.filter(Objects::nonNull)
				.sorted()
				.collect(Collectors.toList());
		
		Columns.NODE_GENES.set(row, prefix, null, gene_list);
		Columns.NODE_ENR_GENES.set(row, prefix, null, enr_gene_list);
		Columns.NODE_GS_DESCR.set(row, prefix, null, sigGeneSet.getDescription());
		Columns.NODE_GS_TYPE.set(row, prefix, null, Columns.NODE_GS_TYPE_SIGNATURE);
		Columns.NODE_NAME.set(row, prefix, null, sigGeneSet.getName());
		Columns.NODE_GS_SIZE.set(row, prefix, null, sigGeneSet.getGenes().size());

		// add the geneset of the signature node to the GenesetsOfInterest,
		// as the Heatmap will grep it's data from there.
		DataSet dataset = map.getDataset(paParams.getSignatureDataSet());
		Set<Integer> signatureGenesInDataSet = ImmutableSet.copyOf(Sets.intersection(sigGeneSet.getGenes(), dataset.getDatasetGenes()));
		GeneSet geneSetInDataSet = new GeneSet(sigGeneSet.getName(), sigGeneSet.getDescription(), signatureGenesInDataSet);
		dataset.getGenesetsOfInterest().getGenesets().put(hub_name, geneSetInDataSet);

		return created;
	}

	/**
	 * Returns true iff the user should be warned about an existing edge that
	 * does not pass the new cutoff. If the edge already exists it will be
	 * returned, if the edge had to be created it will not be returned.
	 */
	private void createEdge(String edge_name, CyNetwork network, CyNetworkView current_view, String prefix,
			CyTable cyEdgeAttrs, CyTable cyNodeAttrs, boolean passed_cutoff) {

		CyEdge edge = NetworkUtil.getEdgeWithValue(network, cyEdgeAttrs, CyNetwork.NAME, edge_name);
		GenesetSimilarity genesetSimilarity = geneset_similarities.get(edge_name);

		if(edge == null) {
			if(passed_cutoff) {
				CyNode hub_node = NetworkUtil.getNodeWithValue(network, cyNodeAttrs, CyNetwork.NAME, genesetSimilarity.getGeneset1_Name());
				CyNode gene_set = NetworkUtil.getNodeWithValue(network, cyNodeAttrs, CyNetwork.NAME, genesetSimilarity.getGeneset2_Name());
				if(hub_node == null || gene_set == null)
					return;
				edge = network.addEdge(hub_node, gene_set, false);
				taskResult.addNewEdge(edge);
			} else {
				return; // edge does not exist and does not pass cutoff, do nothing
			}
		} else {
			if(!passed_cutoff) {
				taskResult.addExistingEdgeFailsCutoff(edge);
			}
		}

		if(passed_cutoff)
			taskResult.incrementPassedCutoffCount();

		CyRow row = cyEdgeAttrs.getRow(edge.getSUID());
		row.set(CyNetwork.NAME, edge_name);
		row.set(CyEdge.INTERACTION, interaction);

		List<String> gene_list = new ArrayList<>();
		Set<Integer> genes_hash = genesetSimilarity.getOverlapping_genes();
		for(Integer current : genes_hash) {
			String gene = map.getGeneFromHashKey(current);
			if(gene != null) {
				gene_list.add(gene);
			}
		}
		Collections.sort(gene_list);

		Columns.EDGE_OVERLAP_GENES.set(row, prefix, null, gene_list);
		Columns.EDGE_OVERLAP_SIZE.set(row, prefix, null, genesetSimilarity.getSizeOfOverlap());
		Columns.EDGE_SIMILARITY_COEFF.set(row, prefix, null, genesetSimilarity.getSimilarity_coeffecient());
		Columns.EDGE_DATASET.set(row, prefix, null, Columns.EDGE_DATASET_VALUE_SIG);
		
		if(passed_cutoff)
			Columns.EDGE_CUTOFF_TYPE.set(row, prefix, null, paParams.getRankTestParameters().getType().display);

		PostAnalysisFilterType filterType = paParams.getRankTestParameters().getType();

		if(filterType.isMannWhitney()) {
			Columns.EDGE_MANN_WHIT_TWOSIDED_PVALUE.set(row, prefix, null, genesetSimilarity.getMann_Whit_pValue_twoSided());
			Columns.EDGE_MANN_WHIT_GREATER_PVALUE.set(row, prefix, null, genesetSimilarity.getMann_Whit_pValue_greater());
			Columns.EDGE_MANN_WHIT_LESS_PVALUE.set(row, prefix, null, genesetSimilarity.getMann_Whit_pValue_less());
			Columns.EDGE_MANN_WHIT_CUTOFF.set(row, prefix, null, paParams.getRankTestParameters().getValue());
		}

		// always calculate hypergeometric
		Columns.EDGE_HYPERGEOM_PVALUE.set(row, prefix, null, genesetSimilarity.getHypergeom_pvalue());
		Columns.EDGE_HYPERGEOM_N.set(row, prefix, null, genesetSimilarity.getHypergeom_N());
		Columns.EDGE_HYPERGEOM_n.set(row, prefix, null, genesetSimilarity.getHypergeom_n());
		Columns.EDGE_HYPERGEOM_m.set(row, prefix, null, genesetSimilarity.getHypergeom_m());
		Columns.EDGE_HYPERGEOM_k.set(row, prefix, null, genesetSimilarity.getHypergeom_k());
		Columns.EDGE_HYPERGEOM_CUTOFF.set(row, prefix, null, paParams.getRankTestParameters().getValue());
	}
	

	private void hypergeometric(int universeSize, Set<Integer> sigGenesInUniverse, Set<Integer> enrGenes, Set<Integer> intersection, GenesetSimilarity comparison) {
		// Calculate Hypergeometric pValue for Overlap
		int N = universeSize; //number of total genes (size of population / total number of balls)
		int n = sigGenesInUniverse.size(); //size of signature geneset (sample size / number of extracted balls)
		int m = enrGenes.size(); //size of enrichment geneset (success Items / number of white balls in population)
		int k = intersection.size(); //size of intersection (successes /number of extracted white balls)
		double hyperPval;

		if(k > 0)
			hyperPval = Hypergeometric.hyperGeomPvalue_sum(N, n, m, k, 0);
		else // Correct p-value of empty intersections to 1 (i.e. not significant)
			hyperPval = 1.0;

		comparison.setHypergeom_pvalue(hyperPval);
		comparison.setHypergeom_N(N);
		comparison.setHypergeom_n(n);
		comparison.setHypergeom_m(m);
		comparison.setHypergeom_k(k);
	}

	private void mannWhitney(Set<Integer> intersection, GenesetSimilarity comparison) {
		// Calculate Mann-Whitney U pValue for Overlap
		Integer[] overlap_gene_ids = intersection.toArray(new Integer[intersection.size()]);

		double[] overlap_gene_scores = new double[overlap_gene_ids.length];
		int j = 0;
		for(Integer gene_id : overlap_gene_ids) {
			Double score = ranks.getScore(gene_id);
			if(score != null) {
				overlap_gene_scores[j++] = score; // unbox
			}
		}

		overlap_gene_scores = Arrays.copyOf(overlap_gene_scores, j);
		

		if(ranks.isEmpty()) {
			comparison.setMann_Whit_pValue_twoSided(1.5); // avoid NoDataException
			comparison.setMann_Whit_pValue_greater(1.5);
			comparison.setMann_Whit_pValue_less(1.5);
			comparison.setMannWhitMissingRanks(true);
		} else {
			double[] scores = ranks.getScores();
			// MKTODO could modify MannWHitneyUTestSided to return all three values from one call
			MannWhitneyUTestSided mann_whit = new MannWhitneyUTestSided();
			double mannPvalTwoSided = mann_whit.mannWhitneyUTest(overlap_gene_scores, scores, MannWhitneyUTestSided.Type.TWO_SIDED);
			comparison.setMann_Whit_pValue_twoSided(mannPvalTwoSided);
			double mannPvalGreater = mann_whit.mannWhitneyUTest(overlap_gene_scores, scores, MannWhitneyUTestSided.Type.GREATER);
			comparison.setMann_Whit_pValue_greater(mannPvalGreater);
			double mannPvalLess = mann_whit.mannWhitneyUTest(overlap_gene_scores, scores, MannWhitneyUTestSided.Type.LESS);
			comparison.setMann_Whit_pValue_less(mannPvalLess);
		}
	}

	private Map<String, CyNode> createNodeMap(CyNetwork current_network, CyTable cyNodeAttrs, String prefix) {
		Map<String, CyNode> nodesMap = new HashMap<>();
		for(CyNode node : current_network.getNodeList()) {
			CyRow row = cyNodeAttrs.getRow(node.getSUID());
			String name = Columns.NODE_NAME.get(row, prefix, null);
			nodesMap.put(name, node);
		}
		return nodesMap;
	}

	/*
	 * Create Node attribute table with post analysis parameters not in the main
	 * EM table
	 */
	private CyTable createNodeAttributes(CyNetwork network, String name, String prefix) {
		CyTable table = network.getDefaultNodeTable();
		Columns.NODE_ENR_GENES.createColumnIfAbsent(table, prefix, null);
		Columns.NODE_GS_SIZE.createColumnIfAbsent(table, prefix, null);
		return table;
	}

	//create the edge attribue table
	private CyTable createEdgeAttributes(CyNetwork network, String name, String prefix) {
		CyTable table = network.getDefaultEdgeTable();
		//check to see if column exists.  If it doesn't then create it
		Columns.EDGE_HYPERGEOM_PVALUE.createColumnIfAbsent(table, prefix, null);
		Columns.EDGE_HYPERGEOM_N.createColumnIfAbsent(table, prefix, null);
		Columns.EDGE_HYPERGEOM_n.createColumnIfAbsent(table, prefix, null);
		Columns.EDGE_HYPERGEOM_k.createColumnIfAbsent(table, prefix, null);
		Columns.EDGE_HYPERGEOM_m.createColumnIfAbsent(table, prefix, null);
		Columns.EDGE_HYPERGEOM_CUTOFF.createColumnIfAbsent(table, prefix, null);
		Columns.EDGE_MANN_WHIT_TWOSIDED_PVALUE.createColumnIfAbsent(table, prefix, null);
		Columns.EDGE_MANN_WHIT_GREATER_PVALUE.createColumnIfAbsent(table, prefix, null);
		Columns.EDGE_MANN_WHIT_LESS_PVALUE.createColumnIfAbsent(table, prefix, null);
		Columns.EDGE_MANN_WHIT_CUTOFF.createColumnIfAbsent(table, prefix, null);
		Columns.EDGE_CUTOFF_TYPE.createColumnIfAbsent(table, prefix, null);
		return table;
	}

	@Override
	public void run(TaskMonitor taskMonitor) throws Exception {
		taskMonitor.setTitle("Generating Signature Hubs");
		buildDiseaseSignature(taskMonitor);
	}

	@Override
	public <R> R getResults(Class<? extends R> type) {
		if(CreateDiseaseSignatureTaskResult.class.equals(type)) {
			taskResult.setCancelled(cancelled);
			return type.cast(taskResult.build());
		}
		return null;
	}
	
	

}