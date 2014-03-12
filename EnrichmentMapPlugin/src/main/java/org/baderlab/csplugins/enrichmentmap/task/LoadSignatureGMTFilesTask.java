package org.baderlab.csplugins.enrichmentmap.task;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import javax.swing.DefaultListModel;

import org.apache.commons.math3.stat.inference.MannWhitneyUTest;
import org.baderlab.csplugins.enrichmentmap.PostAnalysisParameters;
import org.baderlab.csplugins.enrichmentmap.model.DataSet;
import org.baderlab.csplugins.enrichmentmap.model.EnrichmentMap;
import org.baderlab.csplugins.enrichmentmap.model.GeneSet;
import org.baderlab.csplugins.enrichmentmap.model.Rank;
import org.baderlab.csplugins.enrichmentmap.model.Ranking;
import org.baderlab.csplugins.enrichmentmap.parsers.GMTFileReaderTask;
import org.baderlab.csplugins.enrichmentmap.view.PostAnalysisInputPanel;

import cern.jet.stat.Gamma;

import cytoscape.task.Task;
import cytoscape.task.TaskMonitor;
import cytoscape.task.ui.JTaskConfig;

public class LoadSignatureGMTFilesTask implements Task {

        private PostAnalysisParameters paParams = null;
        private PostAnalysisInputPanel paPanel = null;
        private EnrichmentMap map = null;
        private TaskMonitor taskMonitor = null;
        private boolean interrupted = false;
        
        /**
         * constructor w/ TaskMonitor
         * @param paParams
         * @param taskMonitor
         */
        @SuppressWarnings("unused")
        public LoadSignatureGMTFilesTask( EnrichmentMap map, PostAnalysisParameters paParams, TaskMonitor taskMonitor ){
            this(map, paParams );
            this.taskMonitor = taskMonitor;
        }
        
        /**
         * constructor w/o TaskMonitor
         * @param paParams
         */
        public LoadSignatureGMTFilesTask( EnrichmentMap map, PostAnalysisParameters paParams ){
            this.paParams = paParams;
            this.map = map;
        }
        
        /**
         * constructor w map, post-analysis parameters and post-analysis panel
         * @param paParams
         */
        public LoadSignatureGMTFilesTask( EnrichmentMap map, PostAnalysisParameters paParams, PostAnalysisInputPanel paPanel ){
            this.paParams = paParams;
            this.map = map;
            this.paPanel = paPanel;
        }
        /* (non-Javadoc)
         * @see cytoscape.task.Task#getTitle()
         */
        public String getTitle() {
            return new String("Loading Geneset Files...");
        }

        /* (non-Javadoc)
         * @see cytoscape.task.Task#halt()
         */
        public void halt() {
            this.interrupted = true;

        }

        /**
         * @see cytoscape.task.Task#run()
         */
        public void run() {
            //now a Cytoscape Task (LoadSignatureGenesetsTask)
            try {
                try{
                	//TODO:Add signature support
                    //Load the GSEA geneset file
                    //GMTFileReaderTask gmtFile_1 = new GMTFileReaderTask(map,paParams, taskMonitor, GMTFileReaderTask.ENRICHMENT_GMT);
                    //gmtFile_1.run();
    
                    //Load the Disease Signature geneset file
                    GMTFileReaderTask gmtFile_2 = new GMTFileReaderTask(map, paParams, taskMonitor, GMTFileReaderTask.SIGNATURE_GMT);
                    gmtFile_2.run();
                } catch (OutOfMemoryError e) {
                    taskMonitor.setException(e,"Out of Memory. Please increase memory allotment for Cytoscape.");
                    return;
                } catch(Exception e){
                    taskMonitor.setException(e,"unable to load GMT files");
                    return;
                }
                taskMonitor.setStatus("Analyzing genesets ... ");
                //Sort the Genesets:
                DefaultListModel signatureSetNames = paParams.getSignatureSetNames();
                DefaultListModel selectedSignatureSetNames = paParams.getSelectedSignatureSetNames();
                signatureSetNames.clear(); // clear, that we don't have duplicates afterwards - Bug #103 a
                //filter the signature genesets to only include genesets that overlap with the genesets
                //in our current map.
                HashMap<String, GeneSet> genesets_in_map = map.getAllGenesets();
                //get the value to be filtered by if there is a filter
                HashSet<Integer> EnrichmentGenes = new HashSet<Integer>();
                for (Iterator<String> i = genesets_in_map.keySet().iterator(); i.hasNext(); ){
                    String setName = i.next();
                    EnrichmentGenes.addAll(genesets_in_map.get(setName).getGenes());
                }
                // Initialize parameters for Hypergeometric Test
                int N = EnrichmentGenes.size();
                int n = 0;
                int m = 0;
                int k = 0;

                double hyperPval;
                Object signatureGeneset, mapGeneset;
                int percentComplete;
                long timeRemaining;

                Object[] setNamesArray = paParams.getSignatureGenesets().getGenesets().keySet().toArray();
                Arrays.sort(setNamesArray);
                
            	HashMap<String, DataSet> data_sets = this.map.getDatasets();
            	DataSet dataset = data_sets.get(paParams.getSignature_dataSet());
            	Ranking ranks = dataset.getExpressionSets().getRanks().get(paParams.getSignature_rankFile());
            	
                HashMap<Integer, Double> gene2score = ranks.getGene2Score();
                
//                Object[] overlap_gene_ids;
//                double[] overlap_gene_scores;
//                Object[] enr_gene_ids;
//                double[] enr_gene_scores;
//            	double mannPval;
//                MannWhitneyUTest mann_whit;

                
                for (int i = 0; i < setNamesArray.length; i++) {  
                	percentComplete = (int) (((double) i / setNamesArray.length) * 100);
                    timeRemaining = setNamesArray.length - i;
                    taskMonitor.setStatus("Analyzing geneset " + (i + 1) + " of " + setNamesArray.length);
                    taskMonitor.setPercentCompleted(percentComplete);
                    taskMonitor.setEstimatedTimeRemaining(timeRemaining);
                    if (interrupted)
                        throw new InterruptedException();
                    signatureGeneset = setNamesArray[i];
                    if (! selectedSignatureSetNames.contains(signatureGeneset)) {
                        if(paParams.isFilter()) {
                            //only add the name if it overlaps with the sets in the map.
                            boolean matchfound = false;
                            for (Iterator<String> j = genesets_in_map.keySet().iterator(); j.hasNext(); ){
                                mapGeneset = j.next();
                            	//check if this set overlaps with current geneset
                                HashSet <Integer> mapset = new HashSet<Integer>(genesets_in_map.get(mapGeneset).getGenes());
//                                HashSet <Integer> enrGeneSet = (HashSet<Integer>) mapset.clone();
                                Integer original_size = mapset.size();
                                HashSet <Integer> paset = new HashSet<Integer>(paParams.getSignatureGenesets().getGenesets().get(signatureGeneset).getGenes());
                                mapset.retainAll(paset);
                                //if we want to do the hypergeometric test do:
                                if (paParams.getSignature_filterMetric() == paParams.HYPERGEOM) {
                                    // Calculate Hypergeometric pValue for Overlap
                                    //N: number of total genes (size of population / total number of balls)
                                    n = paset.size();  //size of signature geneset (sample size / number of extracted balls)
                                    m = original_size;   //size of enrichment geneset (success Items / number of white balls in population)
                                    k = mapset.size(); //size of intersection (successes /number of extracted white balls)
                                    if (k > 0) 
                                        hyperPval = hyperGeomPvalue_sum(N, n, m, k, 0);
                                    else // Correct p-value of empty intersections to 1 (i.e. not significant)
                                        hyperPval = 1.0;
                                    if (hyperPval <= paParams.getSignature_Hypergeom_Cutoff()) {
                                    	
//                                    	/**
//                                    	 * PERFORM MANN-WHITNEY HERE!!
//                                    	 */
//                                    	                                        
//        	                            overlap_gene_ids = mapset.toArray();
//        	                            overlap_gene_scores = new double[overlap_gene_ids.length];
//        	                            
//        	                            // Get the scores for the overlap
//        	                            for (int p = 0; p < overlap_gene_ids.length; p++) {
//        	                            	overlap_gene_scores[p] = gene2score.get(overlap_gene_ids[p]);
//        	                            }
//        	                            
//        	                            enr_gene_ids = enrGeneSet.toArray();
//        	                            enr_gene_scores = new double[enr_gene_ids.length];
//       	                            
//        	                            // Get the scores for the overlap
//        	                            for (int p = 0; p < enr_gene_ids.length; p++) {
//        	                            	enr_gene_scores[p] = gene2score.get(enr_gene_ids[p]);
//        	                            }
//
//                                    	
//        		                        mann_whit = new MannWhitneyUTest();
//        			                	mannPval = mann_whit.mannWhitneyUTest(overlap_gene_scores, enr_gene_scores);
//        			                	
//            			                System.out.println(mannPval);
//        			                	
//            			                if (mannPval <= 0.1) { 
            			                	matchfound = true;    
            			                	break;
//            			                }
                                    }
                                //if we are looking for percentage do:
                                } else if(paParams.getSignature_filterMetric() == paParams.PERCENT) {
                                    Double relative_per =  mapset.size()/original_size.doubleValue();
                                    if(relative_per >= (Double)(paParams.getFilterValue()/100.0) ){
                                        matchfound = true;
                                        break;
                                    }
                                //if we are looking for number in the overlap
                                } else if(paParams.getSignature_filterMetric() == paParams.NUMBER) {
                                    if(mapset.size() >= paParams.getFilterValue()){
                                        matchfound = true;
                                        break;
                                    }
                                } else if(paParams.getSignature_filterMetric() == paParams.SPECIFIC) {
                                    Double relative_per =  mapset.size()/((Integer)(paset.size())).doubleValue();
                                    if(relative_per >= (Double)(paParams.getFilterValue()/100.0) ){
                                        matchfound = true;
                                        break;
                                    }
                                }
                            }
                            if(matchfound){
                                if (! signatureSetNames.contains(signatureGeneset)) {
                                    signatureSetNames.addElement(signatureGeneset);
                                    this.paPanel.setAvSigCount(signatureSetNames.size());
                                }
                            }
                        } else {
                        	signatureSetNames.addElement(signatureGeneset);
                            this.paPanel.setAvSigCount(signatureSetNames.size());
                        }
                    }
                }
            } catch (InterruptedException e) {
                taskMonitor.setException(e, "loading of GMT files cancelled");
            }
            
        }
        /* (non-Javadoc)
         * @see cytoscape.task.Task#setTaskMonitor(cytoscape.task.TaskMonitor)
         */
        public void setTaskMonitor(TaskMonitor taskMonitor) {
            if (this.taskMonitor != null) {
                throw new IllegalStateException("Task Monitor is already set.");
            }
            this.taskMonitor = taskMonitor;
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

		public static double hyperGeomPvalue_sum(int N, int n, int m, int k) {
		    return hyperGeomPvalue_sum(N, n, m, k, 0);
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
		    double log_p = LoadSignatureGMTFilesTask.binomialLog(m, k) + LoadSignatureGMTFilesTask.binomialLog(N-m, n-k) - LoadSignatureGMTFilesTask.binomialLog(N, n) ;
		    
		    return Math.exp(log_p);
		}

}
