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

import giny.view.NodeView;

import java.util.*;

import org.baderlab.csplugins.enrichmentmap.EnrichmentMapManager;
import org.baderlab.csplugins.enrichmentmap.EnrichmentMapVisualStyle;
import org.baderlab.csplugins.enrichmentmap.PostAnalysisParameters;
import org.baderlab.csplugins.enrichmentmap.model.EnrichmentMap;
import org.baderlab.csplugins.enrichmentmap.model.GeneSet;
import org.baderlab.csplugins.enrichmentmap.model.GenesetSimilarity;

import cytoscape.CyEdge;
import cytoscape.CyNetwork;
import cytoscape.CyNode;
import cytoscape.Cytoscape;
import cytoscape.data.CyAttributes;
import cytoscape.task.Task;
import cytoscape.task.TaskMonitor;
import cytoscape.view.CyNetworkView;

import cern.jet.stat.Gamma;

/**
 * Cytoscape-Task to perform  Disease-Signature Post-Analysis
 * 
 * @author revilo
 * <p>
 * Date   July 10, 2009<br>
 * Time   3:58:24 PM<br>
 *
 */
public class BuildDiseaseSignatureTask implements Task {
    private PostAnalysisParameters paParams;
    private EnrichmentMap map;
//    private EnrichmentMapParameters emParams;
    
    // Keep track of progress for monitoring:
    private TaskMonitor taskMonitor = null;
    private boolean interrupted = false;
    
    private HashMap<String,GeneSet> EnrichmentGenesets;
    private HashMap<String,GeneSet> SignatureGenesets;
    private HashMap<String,GeneSet> SelectedSignatureGenesets;
    // Gene Populations:
    private HashSet<Integer> EnrichmentGenes;
    private HashSet<Integer> SignatureGenes;
        
    private HashMap<String,GenesetSimilarity> geneset_similarities;
    
    /**
     * default constructor
     * @param paParams
     */
    public BuildDiseaseSignatureTask(EnrichmentMap map, PostAnalysisParameters paParams) {
        
    		this.map = map;

    		//create a new instance of the parameters and copy the version received from the input
        //window into this new instance.
    		this.paParams = new PostAnalysisParameters();
        this.paParams.copyFrom(paParams);
        
        this.EnrichmentGenesets   = map.getAllGenesets();
        this.SignatureGenesets    = this.getPaParams().getSignatureGenesets();

        if (map.getGenesetSimilarity() == null)
            this.geneset_similarities = new HashMap<String, GenesetSimilarity>();
        else
            this.geneset_similarities = map.getGenesetSimilarity();

        if (this.paParams.getSignatureGenesets() == null) {
            new HashMap<String,GeneSet>();
        } else
            map.getAllGenesetsOfInterest();
            
        		this.SelectedSignatureGenesets = new HashMap<String, GeneSet>();

        for (int i = 0; i < paParams.getSelectedSignatureSetNames().getSize(); i++){
            this.SelectedSignatureGenesets.put(paParams.getSelectedSignatureSetNames().get(i).toString(),
                    this.SignatureGenesets.get(paParams.getSelectedSignatureSetNames().get(i)));
        }
        // EnrichmentGenes: pool of all genes in Enrichment Gene Sets
        //TODO: get enrichment map genes from enrichment map parameters now that they are computed there.
        EnrichmentGenes = new HashSet<Integer>();
        for (Iterator<String> i = EnrichmentGenesets.keySet().iterator(); i.hasNext(); ){
            String setName = i.next();
            EnrichmentGenes.addAll(EnrichmentGenesets.get(setName).getGenes());
        }
        // SignatureGenes: pool of all genes in Signature Gene Sets
        SignatureGenes = new HashSet<Integer>();
        for (Iterator<String> i = SignatureGenesets.keySet().iterator(); i.hasNext(); ){
            String setName = i.next();
            SignatureGenes.addAll(SignatureGenesets.get(setName).getGenes());
        }
        
    }


    public void buildDiseaseSignature() {

        /* **************************************************
         * Calculate Similarity between Signature Gene Sets *
         * and Enrichment Genesets.                         *
         ****************************************************/
        int maxValue = SelectedSignatureGenesets.size() * EnrichmentGenesets.size();
        int currentProgress = 0;
        double currentNodeY_offset = paParams.getCurrentNodePlacementY_Offset();
        double currentNodeY_increment = 150.0;
        
        try {
            CyNetwork current_network = Cytoscape.getCurrentNetwork();
            CyNetworkView currentNetworkView = Cytoscape.getCurrentNetworkView();
            CyAttributes cyNodeAttrs = Cytoscape.getNodeAttributes();
            CyAttributes cyEdgeAttrs = Cytoscape.getEdgeAttributes();
            CyAttributes cyNetworkAttrs = Cytoscape.getNetworkAttributes();
            
            String prefix = paParams.getAttributePrefix();
            if (prefix == null) {
                prefix = "EM1_";
                paParams.setAttributePrefix(prefix);
            }
            
            // make a HashMap of all Nodes in the Network
            HashMap<String,CyNode> nodesMap = new HashMap<String, CyNode>();
            List<CyNode> nodesList = current_network.nodesList();
            Iterator<CyNode> nodesIterator = nodesList.iterator();
            
            while (nodesIterator.hasNext()){
                CyNode aNode = (CyNode)nodesIterator.next();
                nodesMap.put(aNode.getIdentifier(), aNode);
            }
            
            // Common gene universe: Intersection of EnrichmentGenes and SignatureGenes
            HashSet<Integer> geneUniverse = new HashSet<Integer>();
            geneUniverse.addAll(EnrichmentGenes);

            /* bug: #97: Post-analysis: thresholding not working with overlap 
             * Don't restrict Universe to Intersection of Enrichment- and Signature Genes 
             * but rather the Universe of all Enrichment Genes.  
             */
            // geneUniverse.retainAll(SignatureGenes); 
            int universeSize = geneUniverse.size();
            
            //iterate over selected Signature genesets
            for (Iterator<String> i = SelectedSignatureGenesets.keySet().iterator(); i.hasNext(); ){
                String hub_name = i.next().toString();
                
                // get the Signature Genes, restrict them to the Gene-Universe and add them to the Parameters
                GeneSet sigGeneSet = SelectedSignatureGenesets.get(hub_name);

                /** 
                 * the signature genes in this signature gene set 
                 */
                HashSet<Integer> sigGenes = sigGeneSet.getGenes();

                /** 
                 * the genes that are in this signature gene set 
                 * as well as in the Universe of Enrichment-GMT Genes.    
                 */
                HashSet<Integer> sigGenesInUniverse = new HashSet<Integer>(sigGenes);
                sigGenesInUniverse.retainAll(geneUniverse);
//                sigGeneSet.setGenes(sigGenes);
                
                EnrichmentMapManager.getInstance().getMap(current_network.getIdentifier()).getSignatureGenesets().put(hub_name, sigGeneSet);
                
                // iterate over Enrichment Genesets
                for (Iterator<String> j = EnrichmentGenesets.keySet().iterator(); j.hasNext();) {
                    String geneset_name = j.next().toString();

                    // Calculate Percentage.  This must be a value between 0..100.
                    int percentComplete = (int) (((double) currentProgress / maxValue) * 100);
                    //  Estimate Time Remaining
                    long timeRemaining = maxValue - currentProgress;
                    if (taskMonitor != null) {
                       taskMonitor.setPercentCompleted(percentComplete);
                       taskMonitor.setStatus("Computing Geneset similarity " + currentProgress + " of " + maxValue);
                       taskMonitor.setEstimatedTimeRemaining(timeRemaining);
                    }
                    currentProgress++;
                    
                    if (interrupted) {
                        throw new InterruptedException();
                    }
                    
                    // Calculate overlap:
                    
                    //Check to see if this comparison has been done
                    //The key for the set of geneset similarities is the
                    //combination of the two names.  Check for either variation name1_name2
                    //or name2_name1
                    String similarity_key1 = hub_name     + " (" + PostAnalysisParameters.SIGNATURE_INTERACTION_TYPE + ") " + geneset_name;
                    String similarity_key2 = geneset_name + " (" + PostAnalysisParameters.SIGNATURE_INTERACTION_TYPE + ") " + hub_name;

                    //first check to see if the terms are the same
                    if(hub_name.equalsIgnoreCase(geneset_name)) {
                       //don't compare two identical genesets
                    }
                    else if (! nodesMap.containsKey(geneset_name)) {
                        // skip if the Geneset is not in the Network
                    } 
                    else if (cyNodeAttrs.getStringAttribute(nodesMap.get(geneset_name).getIdentifier(),
                            prefix + EnrichmentMapVisualStyle.GS_TYPE).equalsIgnoreCase(
                            EnrichmentMapVisualStyle.GS_TYPE_SIGNATURE)) {
                        // skip if the Geneset is a Signature Node from a previous analysis
                    }
                    else if(geneset_similarities.containsKey(similarity_key1) || geneset_similarities.containsKey(similarity_key2)){
                        //skip this geneset comparison.  It has already been done.
                    }
                    else{
                        //get the Enrichment geneset
                        GeneSet enrGeneset = EnrichmentGenesets.get(geneset_name);

                        HashSet<Integer> enrGenes = enrGeneset.getGenes();

                        // restrict to a common gene universe
                        enrGenes.retainAll(geneUniverse);

                        //Get the intersection
                        Set<Integer> intersection = new HashSet<Integer>(sigGenes);
                        intersection.retainAll(enrGenes);

                        //Get the union of the two sets
                        Set<Integer> union = new HashSet<Integer>(sigGenes);
                        union.addAll(enrGenes);

                        double coeffecient;

                        // if  either Jaccard or Overlap similarity are requested:
                        if (paParams.getSignature_CutoffMetric() == PostAnalysisParameters.DIR_OVERLAP) {
                            coeffecient = (double)intersection.size() /  (double) enrGenes.size();
                        } 
                        else if (paParams.getSignature_CutoffMetric() == PostAnalysisParameters.JACCARD){
                            //compute Jaccard similarity
                            coeffecient = (double)intersection.size() / (double)union.size();
                        }
                        else if(paParams.getSignature_CutoffMetric() == PostAnalysisParameters.OVERLAP){
                            //compute Overlap similarity
                            coeffecient = (double)intersection.size() / Math.min((double)sigGenes.size(), (double)enrGenes.size());
                        } else {
                            // use Directed Overlap
                            coeffecient = (double)intersection.size() /  (double) enrGenes.size();
//                            // use setting from Enrichment Analysis
//                            if (paParams.isJaccard() ) {
//                                //compute Jaccard similarity
//                                coeffecient = (double)intersection.size() / (double)union.size();
//                            } else {
//                                //compute Overlap similarity
//                                coeffecient = (double)intersection.size() / Math.min((double)sigGenes.size(), (double)enrGenes.size());
//                            }
                        }
                        
                        
                        
                        //create Geneset similarity object
                        GenesetSimilarity comparison = new GenesetSimilarity(hub_name, geneset_name, coeffecient, PostAnalysisParameters.SIGNATURE_INTERACTION_TYPE, (HashSet<Integer>)intersection);
                        
                        // Calculate Hypergeometric pValue for Overlap
                        int N = universeSize;               //number of total genes      (size of population / total number of balls)
                        int n = sigGenesInUniverse.size();  //size of signature geneset  (sample size / number of extracted balls)
                        int m = enrGenes.size();            //size of enrichment geneset (success Items / number of white balls in population)
                        int k = intersection.size();        //size of intersection       (successes /number of extracted white balls)
                        double hyperPval;
                        
                        if (k > 0) 
                            hyperPval = hyperGeomPvalue_sum(N, n, m, k, 0);
                        else // Correct p-value of empty intersections to 1 (i.e. not significant)
                            hyperPval = 1.0;
                        
                        comparison.setHypergeom_pvalue(hyperPval);
                            
                        geneset_similarities.put(similarity_key1,comparison);
                    }
                } // End: iterate over Enrichment Genesets
                
                /* ***************************
                 * Create Signature Hub Node *
                 *****************************/
                CyNode hub_node = Cytoscape.getCyNode(hub_name, true);
                current_network.addNode(hub_node);
                
                // add currentNodeY_offset to initial Y position of the Node
                // and increase currentNodeY_offset for the next Node
                NodeView hubNodeView = currentNetworkView.getNodeView(hub_node);
                double hubNodeY = hubNodeView.getYPosition();
                hubNodeView.setYPosition(hubNodeY + currentNodeY_offset);
                currentNodeY_offset += currentNodeY_increment;
                
                String formatted_label =  VisualizeEnrichmentMapTask.formatLabel(hub_node.getIdentifier());
                cyNodeAttrs.setAttribute(hub_node.getIdentifier(), prefix + EnrichmentMapVisualStyle.FORMATTED_NAME, formatted_label);

                //create an attribute that stores the genes that are associated with this node as an attribute list
                //only create the list if the hashkey 2 genes is not null Otherwise it take too much time to populate the list
//                GeneSet sigGeneSet = SelectedSignatureGenesets.get(hub_name);
                if(map.getHashkey2gene() != null){
                    // HashSet to List:
                    List<String> gene_list = new ArrayList<String>();
                    HashSet<Integer> genes_hash = sigGeneSet.getGenes();
                    for(Iterator<Integer> j=genes_hash.iterator(); j.hasNext();){
                        Integer current = j.next();
                        String gene = map.getGeneFromHashKey(current);
                        if(gene_list != null)
                            gene_list.add(gene);
                    }
                    Collections.sort(gene_list);

                    List<String> enr_gene_list = new ArrayList<String>();
                    HashSet<Integer> enr_genes_hash = sigGeneSet.getGenes();
                    enr_genes_hash.retainAll(geneUniverse);
                    for(Iterator<Integer> j=enr_genes_hash.iterator(); j.hasNext();){
                        Integer current = j.next();
                        String gene = map.getGeneFromHashKey(current);
                        if(enr_gene_list != null)
                            enr_gene_list.add(gene);
                    }
                    Collections.sort(enr_gene_list);
                    
                    cyNodeAttrs.setListAttribute(hub_node.getIdentifier(), prefix + EnrichmentMapVisualStyle.GENES, gene_list);
                    cyNodeAttrs.setListAttribute(hub_node.getIdentifier(), prefix + EnrichmentMapVisualStyle.ENR_GENES, enr_gene_list);
                   
                    cyNodeAttrs.setAttribute(hub_node.getIdentifier(), prefix + EnrichmentMapVisualStyle.GS_DESCR, sigGeneSet.getDescription() );
                    cyNodeAttrs.setAttribute(hub_node.getIdentifier(), prefix + EnrichmentMapVisualStyle.GS_TYPE, EnrichmentMapVisualStyle.GS_TYPE_SIGNATURE);
                    cyNodeAttrs.setAttribute(hub_node.getIdentifier(), prefix + EnrichmentMapVisualStyle.NAME, sigGeneSet.getName() );
                    cyNodeAttrs.setAttribute(hub_node.getIdentifier(), prefix + EnrichmentMapVisualStyle.GS_SIZE_SIGNATURE , sigGeneSet.getGenes().size() );
                }

                // add the geneset of the signature node to the GenesetsOfInterest,
                // as the Heatmap will grep it's data from there.
                //TODO: Currently only supports one dataset
                //TODO:Enable signature dataset with multiple dataset
                
                sigGeneSet.getGenes().retainAll(map.getDataset(EnrichmentMap.DATASET1).getDatasetGenes());
                map.getDataset(EnrichmentMap.DATASET1).getGenesetsOfInterest().getGenesets().put(hub_name, sigGeneSet);
                
                // set Visual Style bypass
                cyNodeAttrs.setAttribute(hub_node.getIdentifier(), "node.shape", paParams.getSignatureHub_nodeShape());
                cyNodeAttrs.setAttribute(hub_node.getIdentifier(), "node.fillColor", paParams.getSignatureHub_nodeColor());
                cyNodeAttrs.setAttribute(hub_node.getIdentifier(), "node.borderColor", paParams.getSignatureHub_borderColor());
                
            }// End: iterate over Signature Genesets
            
            paParams.setCurrentNodePlacementY_Offset(currentNodeY_offset);
            
            /* ****************************
             * Create Signature Hub Edges *
             ******************************/

            for (Iterator<String> i = geneset_similarities.keySet().iterator(); i.hasNext() ;) {
                if (interrupted) {
                    throw new InterruptedException();
                }
                String edge_name = i.next().toString();
                
                if (! geneset_similarities.get(edge_name).getInteractionType().equals(PostAnalysisParameters.SIGNATURE_INTERACTION_TYPE))
                    // skip if it's not a signature edge
                    continue;
                if (!   (   this.SelectedSignatureGenesets.containsKey(geneset_similarities.get(edge_name).getGeneset1_Name()) 
                         || this.SelectedSignatureGenesets.containsKey(geneset_similarities.get(edge_name).getGeneset2_Name()) ) )   
                    // skip if not either of the adjacent nodes is a SelectedSignatureGenesets of the current analysis (fixes Bug #44)
                    continue;

                // check if combination passes Cut-Off:
                boolean passed_cutoff = false;
                if ( (paParams.getSignature_CutoffMetric() == PostAnalysisParameters.ABS_NUMBER) && 
                     (geneset_similarities.get(edge_name).getSizeOfOverlap() >= paParams.getSignature_absNumber_Cutoff() ) )
                    passed_cutoff = true;
                else if ( (paParams.getSignature_CutoffMetric() == PostAnalysisParameters.JACCARD) && 
                          (geneset_similarities.get(edge_name).getSimilarity_coeffecient() >= paParams.getSignature_Jaccard_Cutoff() ) )
                    passed_cutoff = true;
                else if ( (paParams.getSignature_CutoffMetric() == PostAnalysisParameters.OVERLAP) && 
                        (geneset_similarities.get(edge_name).getSimilarity_coeffecient() >= paParams.getSignature_Overlap_Cutoff() ) )
                    passed_cutoff = true;
                else if ( (paParams.getSignature_CutoffMetric() == PostAnalysisParameters.DIR_OVERLAP) && 
                        (geneset_similarities.get(edge_name).getSimilarity_coeffecient() >= paParams.getSignature_DirOverlap_Cutoff() ) )
                    passed_cutoff = true;
                else if ( (paParams.getSignature_CutoffMetric() == PostAnalysisParameters.HYPERGEOM) && 
                          (geneset_similarities.get(edge_name).getHypergeom_pvalue() != -1.0) &&
                          (geneset_similarities.get(edge_name).getHypergeom_pvalue() <= paParams.getSignature_Hypergeom_Cutoff() ) )
                    passed_cutoff = true;

                if (passed_cutoff) {
                    CyNode hub_node = Cytoscape.getCyNode( geneset_similarities.get(edge_name).getGeneset1_Name() );
                    CyNode gene_set = Cytoscape.getCyNode( geneset_similarities.get(edge_name).getGeneset2_Name() );
   
                    CyEdge edge = Cytoscape.getCyEdge(hub_node, gene_set, "interaction", PostAnalysisParameters.SIGNATURE_INTERACTION_TYPE, true);
                    current_network.addEdge(edge);
                    
                    //create an attribute that stores the genes that are associated with this edge as an attribute list
                    //only create the list if the hashkey 2 genes is not null Otherwise it take too much time to populate the list
                    if(map.getHashkey2gene() != null){
                        List<String> gene_list = new ArrayList<String>();
                        HashSet<Integer> genes_hash = geneset_similarities.get(edge_name).getOverlapping_genes();
                        for(Iterator<Integer> k=genes_hash.iterator(); k.hasNext();){
                            Integer current = k.next();
                            String gene = map.getGeneFromHashKey(current);
                            if(gene_list != null)
                                gene_list.add(gene);
                        }
                        Collections.sort(gene_list);
                        
                        cyEdgeAttrs.setListAttribute(edge.getIdentifier(), prefix+EnrichmentMapVisualStyle.OVERLAP_GENES, gene_list);                        
                    }
 
                    cyEdgeAttrs.setAttribute(edge.getIdentifier(), prefix + EnrichmentMapVisualStyle.OVERLAP_SIZE       , geneset_similarities.get(edge_name).getSizeOfOverlap());
                    cyEdgeAttrs.setAttribute(edge.getIdentifier(), prefix + EnrichmentMapVisualStyle.SIMILARITY_COEFFECIENT, geneset_similarities.get(edge_name).getSimilarity_coeffecient());
                    cyEdgeAttrs.setAttribute(edge.getIdentifier(), prefix + EnrichmentMapVisualStyle.HYPERGEOM_PVALUE   , geneset_similarities.get(edge_name).getHypergeom_pvalue());
                    cyEdgeAttrs.setAttribute(edge.getIdentifier(), prefix + EnrichmentMapVisualStyle.ENRICHMENT_SET  , geneset_similarities.get(edge_name).getEnrichment_set());
                    
                    cyEdgeAttrs.setAttribute(edge.getIdentifier(), "edge.color", paParams.getSignatureHub_edgeColor());
                    //change "edge.lineWidth" based on Hypergeometric Value 
                    if (geneset_similarities.get(edge_name).getHypergeom_pvalue() <= (paParams.getSignature_Hypergeom_Cutoff()/100) )
                        cyEdgeAttrs.setAttribute(edge.getIdentifier(), "edge.lineWidth", "8.0");
                    else 
                    if (geneset_similarities.get(edge_name).getHypergeom_pvalue() <= (paParams.getSignature_Hypergeom_Cutoff()/10) )
                        cyEdgeAttrs.setAttribute(edge.getIdentifier(), "edge.lineWidth", "4.5");
                    else
                        cyEdgeAttrs.setAttribute(edge.getIdentifier(), "edge.lineWidth", "1.0");
                    

                } //if (geneset_similarities.get(edge_name).getSizeOfOverlap() > 0)
            } //for
 
            currentNetworkView.redrawGraph(false, true);
            cyNetworkAttrs.setAttribute(currentNetworkView.getIdentifier(), EnrichmentMapVisualStyle.NUMBER_OF_ENRICHMENT_GENES, geneUniverse.size());
        } catch (InterruptedException e) {
            taskMonitor.setException(e, "Generation of Signature Hubs cancelled");
        }
    }
    
    
    /**
     * Calculate the p-Value of the Hypergeometric Distribution<p>
     * 
     *  from:  http://en.wikipedia.org/wiki/Hypergeometric_distribution<p>
     * 
     * P(X=k) = {m over k} * { (N-m) over (n-k) } / {N over n}
     * 
     * @param N size of the population  (Universe of genes)
     * @param n size of the sample      (signature geneset) 
     * @param m successes in population (enrichment geneset)
     * @param k successes in sample     (intersection of both genesets)
     * 
     * @return the p-Value of the Hypergeometric Distribution for P(X=k)
     */
    public static double hyperGeomPvalue(int N, int n, int m, int k) {
        //calculating in logarithmic scale as we are dealing with large numbers. 
        double log_p = binomialLog(m, k) + binomialLog(N-m, n-k) - binomialLog(N, n) ;
        
        return Math.exp(log_p);
    }
    
    /**
     * Calculate sum over distinct p-Values of the Hypergeometric Distribution<p>
     * 
     * for 
     * P(X &ge; k) : Probability to get k or more successes in the sample with a size of n<br>
     * P(X &gt; k) : Probability to get more that k successes in the sample with a size of n<br>
     * P(X &le; k) : Probability to get k or less successes in the sample with a size of n<br>
     * P(X &lt; k) : Probability to get less than k successes in the sample with a size of n<p>
     * 
     * @param N size of the population  (Universe of genes)
     * @param n size of the sample      (signature geneset) 
     * @param m successes in population (enrichment geneset)
     * @param k successes in sample     (intersection of both genesets)
     * @param mode = 0 : P(X &ge; k) (default)<br>
     *        mode = 1 : P(X &gt; k) (behavior of R with "lower.tail=FALSE")<br>  
     *        mode = 2 : P(X &le; k) (behavior of R with "lower.tail=TRUE")<br>
     *        mode = 3 : P(X &lt; k)<br>
     * 
     * @return the p-Value of the Hypergeometric Distribution for P(X>=k)
     */
    public static double hyperGeomPvalue_sum(int N, int n, int m, int k, int mode) {
        // the number of successes in the sample (k) cannot be larger than the sample (n) or the number of total successes (m)
        double sum = 0.0;
        int kMax;
        switch (mode) {
        case 0:
            kMax = Math.min(n,m);
            for (int k_prime = k; k_prime <= kMax; k_prime++ ){
                sum += hyperGeomPvalue(N, n, m, k_prime);
            }
            break;

        case 1:
            kMax = Math.min(n,m);
            for (int k_prime = k+1; k_prime <= kMax; k_prime++ ){
                sum += hyperGeomPvalue(N, n, m, k_prime);
            }
            break;

        case 2:
            for (int k_prime = k; k_prime >= 0; k_prime-- ){
                sum += hyperGeomPvalue(N, n, m, k_prime);
            }
            break;

        case 3:
            for (int k_prime = k-1; k_prime >= 0; k_prime-- ){
                sum += hyperGeomPvalue(N, n, m, k_prime);
            }
            break;
            
         default:
             break;
         }
        return sum;
    }
    public static double hyperGeomPvalue_sum(int N, int n, int m, int k) {
        return hyperGeomPvalue_sum(N, n, m, k, 0);
    }
    
    /**
     * Calculate the log of Binomial coefficient "n over k" aka "n choose k"
     * 
     * adapted from http://code.google.com/p/beast-mcmc/source/browse/trunk/src/dr/math/Binomial.java?spec=svn1660&r=1660
     * @version Id: Binomial.java,v 1.11 2005/05/24 20:26:00 rambaut Exp
     * Licensed under "LGPL 2.1 or later"
     * 
     * original by:
     * @author Andrew Rambaut
     * @author Alexei Drummond
     * @author Korbinian Strimmer
     * 
     * adapted for using cern.jet.stat:
     * @author Oliver Stueker
     * 
     * @param n
     * @param k
     * @return the binomial coefficient "n over k"
     */
    public static double binomialLog(int n, int k) {
        return (Gamma.logGamma(n + 1.0) - Gamma.logGamma(k + 1.0) - Gamma.logGamma(n - k + 1.0));
    }

    /* ***************************************
     * getters and setters                   *
     *****************************************/
    /**
     * @param paParams the paParams to set
     */
    public void setPaParams(PostAnalysisParameters paParams) {
        this.paParams = paParams;
    }

    /**
     * @return the paParams
     */
    public PostAnalysisParameters getPaParams() {
        return paParams;
    }

    
    
    // ***************************************
    // from here: Auto-generated method stubs!
    // ***************************************
    /**
     * @see cytoscape.task.Task#run()
     */
    public void run() {
    	buildDiseaseSignature();
    }

    /**
     * @see cytoscape.task.Task#getTitle()
     */
    public String getTitle() {
        return new String("Generating Signature Hubs");
    }

    /**
     * @see cytoscape.task.Task#halt()
     */
    public void halt() {
        this.interrupted = true;

    }

    /**
     * @see cytoscape.task.Task#setTaskMonitor(cytoscape.task.TaskMonitor)
     */
    public void setTaskMonitor(TaskMonitor taskMonitor)
            throws IllegalThreadStateException {

        if (this.taskMonitor != null) {
            throw new IllegalStateException("Task Monitor is already set.");
        }
        this.taskMonitor = taskMonitor;
    }

}