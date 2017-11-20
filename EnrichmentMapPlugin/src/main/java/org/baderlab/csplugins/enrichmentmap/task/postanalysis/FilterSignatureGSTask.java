package org.baderlab.csplugins.enrichmentmap.task.postanalysis;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.baderlab.csplugins.enrichmentmap.model.EnrichmentMap;
import org.baderlab.csplugins.enrichmentmap.model.PostAnalysisFilterType;
import org.baderlab.csplugins.enrichmentmap.model.SetOfGeneSets;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.ObservableTask;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.TaskMonitor.Level;

public class FilterSignatureGSTask extends AbstractTask implements ObservableTask {

	private final EnrichmentMap map;
	private final SetOfGeneSets signatureGenesets;
	private final FilterMetric filterMetric;
	
	private Set<String> resultSignatureSetNames;
	
	
	public FilterSignatureGSTask(EnrichmentMap map, SetOfGeneSets signatureGenesets, FilterMetric filterMetric) {
		this.map = map;
		this.signatureGenesets = signatureGenesets;
		this.filterMetric = filterMetric;
	}
	
	
	@Override
	public void run(TaskMonitor taskMonitor) throws Exception {
		taskMonitor.setTitle("filtering Signature Gene set file");
		filterSignatureGS(taskMonitor);
	}

	
	private void filterSignatureGS(TaskMonitor taskMonitor) {
		resultSignatureSetNames = new HashSet<>();

		//filter the signature genesets to only include genesets that overlap with the genesets in our current map.

		// Use the same genesets that are saved to the session file (bug #66)
		// HashMap<String, GeneSet> genesets_in_map = map.getAllGenesets();
		Map<String, Set<Integer>> genesets_in_map = map.unionAllGeneSetsOfInterest();

		String[] setNamesArray = signatureGenesets.getGeneSets().keySet().toArray(new String[0]);
		Arrays.sort(setNamesArray);

		if(taskMonitor != null)
			taskMonitor.setStatusMessage("Analyzing " + setNamesArray.length + " genesets");

		for(int i = 0; i < setNamesArray.length; i++) {

			int percentComplete = (int) (((double) i / setNamesArray.length) * 100);
			if(taskMonitor != null)
				taskMonitor.setProgress(percentComplete);
			if(cancelled) {
				taskMonitor.showMessage(Level.ERROR, "loading of GMT files cancelled");
				return;
			}
			String signatureGeneset = setNamesArray[i];


			boolean matchfound = false;

			if(filterMetric.getFilterType() != PostAnalysisFilterType.NO_FILTER) {
				//only add the name if it overlaps with the sets in the map.
				for(String mapGeneset : genesets_in_map.keySet()) {
					
					Set<Integer> geneSet = new HashSet<>(genesets_in_map.get(mapGeneset));
					Set<Integer> sigSet  = new HashSet<>(signatureGenesets.getGeneSets().get(signatureGeneset).getGenes());

					matchfound = filterMetric.passes(geneSet, sigSet);
					if(matchfound)
						break;
				}
			} else {
				matchfound = true;
			}

			if(matchfound) {
				resultSignatureSetNames.add(signatureGeneset);
			}
		}
	}

	
	/**
	 * type Set.class for filtered gene set names
	 */
	@Override
	public <R> R getResults(Class<? extends R> type) {
		if(Set.class.equals(type)) {
			return type.cast(resultSignatureSetNames);
		}
		if(SetOfGeneSets.class.equals(type)) {
			return type.cast(signatureGenesets);
		}
		return null;
	}

}
