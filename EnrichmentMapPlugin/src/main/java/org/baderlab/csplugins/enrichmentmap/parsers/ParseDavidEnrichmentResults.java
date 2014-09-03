package org.baderlab.csplugins.enrichmentmap.parsers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Scanner;

import org.baderlab.csplugins.enrichmentmap.model.DataSet;
import org.baderlab.csplugins.enrichmentmap.model.EnrichmentResult;
import org.baderlab.csplugins.enrichmentmap.model.GeneSet;
import org.baderlab.csplugins.enrichmentmap.model.GenericResult;
import org.baderlab.csplugins.enrichmentmap.model.SetOfEnrichmentResults;
import org.cytoscape.io.util.StreamUtil;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.TaskMonitor;

public class ParseDavidEnrichmentResults extends AbstractTask{
	//default Score at Max value
    public static final Double DefaultScoreAtMax = -1000000.0;
	
    //private EnrichmentMapParameters params;
    private DataSet dataset;
    //enrichment results file name
    private String EnrichmentResultFileName1;
    private String EnrichmentResultFileName2;
    
    //Stores the enrichment results
    private SetOfEnrichmentResults enrichments;
    private HashMap<String, EnrichmentResult> results ;

    //phenotypes defined by user - used to classify phenotype specifications
    //in the generic enrichment results file
    private String upPhenotype;
    private String downPhenotype;

    // Keep track of progress for monitoring:
    private TaskMonitor taskMonitor = null;
    private boolean interrupted = false;


    //services needed
    private StreamUtil streamUtil;


	public ParseDavidEnrichmentResults(DataSet dataset, StreamUtil streamUtil) {
		super();
		this.dataset = dataset;
		this.streamUtil = streamUtil;
		
		 this.EnrichmentResultFileName1 = dataset.getEnrichments().getFilename1();
	     this.EnrichmentResultFileName2 = dataset.getEnrichments().getFilename2();

	     //create a new enrichment results set
	     enrichments = dataset.getEnrichments();
	     results = enrichments.getEnrichments();
	     upPhenotype = enrichments.getPhenotype1(); 
	     downPhenotype = enrichments.getPhenotype2();
	       
	}

	/**
     * Parse enrichment results file
     */

    public void parse()  throws IOException{
 	
    		if(this.EnrichmentResultFileName1 != null && !this.EnrichmentResultFileName1.isEmpty())
    			readFile(this.EnrichmentResultFileName1);
    		if(this.EnrichmentResultFileName2 != null && !this.EnrichmentResultFileName2.isEmpty())
    			readFile(this.EnrichmentResultFileName2);
         

    }
    
    /*
     * Read file
     */

    public void readFile(String EnrichmentResultFileName) throws IOException{
    		
    	//open Enrichment Result file
    	InputStream reader = streamUtil.getInputStream(EnrichmentResultFileName);
    			
    	String fullText = new Scanner(reader,"UTF-8").useDelimiter("\\A").next();                        
  			
    	String []lines = fullText.split("\r\n?|\n");

    	//ES and NES columns are specific to the GSEA format
    	String header_line = lines[0];
    	String [] tokens = header_line.split("\t");

    	parseDavidFile(lines);
    			
    }//end of method
    
    /**
     * Parse david enrichment results file
     *
     * @param lines - contents of results file
     */
    public void parseDavidFile(String [] lines){

        //with David results there are no genesets defined.  first pass through the file
        // needs to parse the genesets

        //parameters that can be extracted from David files:
        //Category	Term	Count	%	PValue	Genes	List Total	Pop Hits	Pop Total	Fold Enrichment	Bonferroni	Benjamini	FDR
        // Count = number of genes in the geneset that came from the input list, number of genes in the genelist mapping toa specific term.
        // List Total - number of genes in the gene list mapping to the category (ie. GO Cellular component)
        // Pop Hits - number of genes in the background gene list mapping to a specific term
        // Pop total - number of gene s in the background gene list mapping to the category (i.e. Go Cellular Component)


        // Column 2 is the geneset name
        // Column 1 is the category (and can be used for the description)
        // Column 6 is the list of genes (from the loaded list) in this geneset -- therefore pre-filtered.
        HashMap<String, GeneSet> genesets = dataset.getSetofgenesets().getGenesets();

        //get the genes (which should also be empty
        HashMap<String, Integer> genes = dataset.getMap().getGenes();
        HashMap<Integer, String> key2gene = dataset.getMap().getHashkey2gene();

        int currentProgress = 0;
        int maxValue = lines.length;
        boolean FDR = true;

         //skip the first line which just has the field names (start i=1)
        //check to see how many columns the data has
        String line = lines[0];
        String [] tokens = line.split("\t");
        int length = tokens.length;
        if (length != 13)
            throw new IllegalThreadStateException("David results file is missing data.");
            //not enough data in the file!!

        for (int i = 1; i < lines.length; i++) {
            line = lines[i];

            tokens = line.split("\t");

            double pvalue = 1.0;
            double FDRqvalue = 1.0;
            GenericResult result;
            int gs_size = 0;
            double NES = 1.0;

            //The second column of the file is the name of the geneset
            String name = tokens[1].toUpperCase().trim();

            //the first column of the file is the description
            String description = tokens[0].toUpperCase();

            //when there are two different species it is possible that the gene set could
            //already exist in the set of genesets.  if it does exist then add the genes
            //in this set to the geneset
            GeneSet gs;
            if(genesets.containsKey(name))
                gs = genesets.get(name);

            //load the geneset and the genes to their respective data structures.
            //create an object of type Geneset with the above Name and description
            else
                gs = new GeneSet(name, description);

            String[] gene_tokens = tokens[5].split(", ");

            //All subsequent fields in the list are the geneset associated with this geneset.
            for (int j = 0; j < gene_tokens.length; j++) {

                String gene = gene_tokens[j].toUpperCase();
                    //Check to see if the gene is already in the hashmap of genes
                    //if it is already in the hash then get its associated key and put it
                    //into the set of genes
                if (genes.containsKey(gene)) {
                        gs.addGene(genes.get(gene));
                }

                //If the gene is not in the list then get the next value to be used and put it in the list
                else{
                    if(!gene.equalsIgnoreCase("")){

                        //add the gene to the master list of genes
                        int value = dataset.getMap().getNumberOfGenes();
                        genes.put(gene, value);
                        key2gene.put(value,gene);
                        dataset.getMap().setNumberOfGenes(value+1);

                        //add the gene to the genelist
                        gs.addGene(genes.get(gene));
                    }
                }
            }

            //finished parsing that geneset
            //add the current geneset to the hashmap of genesets
            genesets.put(name, gs);


            //The 5th column is the nominal p-value
            if(tokens[4].equalsIgnoreCase("")){
                //do nothing
            }else{
                pvalue = Double.parseDouble(tokens[4]);
            }

            //the Pop hits is the size of the geneset
            //the Count is the size of the geneset (restricted by the gene list)
            if(tokens[2].equalsIgnoreCase("")){
                //do nothing
            }else{
                gs_size = Integer.parseInt(tokens[2]);
            }

            //Use the Benjamini value for the fdr
            if(tokens[11].equalsIgnoreCase("")){
                //do nothing
            }else{
                FDRqvalue = Double.parseDouble(tokens[11]);
            }

            result = new GenericResult(name,description,pvalue,gs_size,FDRqvalue);



            // Calculate Percentage.  This must be a value between 0..100.
            int percentComplete = (int) (((double) currentProgress / maxValue) * 100);
            //  Estimate Time Remaining
            long timeRemaining = maxValue - currentProgress;
            if (taskMonitor != null) {
                    taskMonitor.setProgress(percentComplete);
                    taskMonitor.setStatusMessage("Parsing Generic Results file " + currentProgress + " of " + maxValue);
                }
            currentProgress++;

             //check to see if the gene set has already been entered in the results
             //it is possible that one geneset will be in both phenotypes.
             //if it is already exists then we want to make sure the one retained is the result with the
             //lower p-value.
             //ticket #149
          GenericResult temp = (GenericResult)results.get(name);
             if(temp == null)
                results.put(name, result);
            else{
                 if(result.getPvalue() < temp.getPvalue())
                    results.put(name, result);
             }

        }
        if(FDR)
        	dataset.getMap().getParams().setFDR(FDR);
    }

    
    /**
     * Non-blocking call to interrupt the task.
     */
    public void halt() {
        this.interrupted = true;
    }

     /**
     * Sets the Task Monitor.
     *
     * @param taskMonitor TaskMonitor Object.
     */
    public void setTaskMonitor(TaskMonitor taskMonitor) {
        if (this.taskMonitor != null) {
            throw new IllegalStateException("Task Monitor is already set.");
        }
        this.taskMonitor = taskMonitor;
    }

    /**
     * Gets the Task Title.
     *
     * @return human readable task title.
     */
    public String getTitle() {
        return new String("Parsing Enrichment Result file");
    }
    
	@Override
	public void run(TaskMonitor taskMonitor) throws Exception {
		this.taskMonitor = taskMonitor;
		this.taskMonitor.setTitle("Parsing David Enrichment Result file");
		
		parse();
		
	}
    
    
}