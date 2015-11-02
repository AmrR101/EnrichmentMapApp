package org.baderlab.csplugins.enrichmentmap.task;

import static org.junit.Assert.*;

import java.util.Map;
import java.util.Set;

import org.baderlab.csplugins.enrichmentmap.EnrichmentMapParameters;
import org.baderlab.csplugins.enrichmentmap.FilterParameters.FilterType;
import org.baderlab.csplugins.enrichmentmap.PostAnalysisParameters;
import org.baderlab.csplugins.enrichmentmap.model.DataSetFiles;
import org.baderlab.csplugins.enrichmentmap.model.EnrichmentMap;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 * Test FilterTypes: NUMBER, PERCENT, SPECIFIC.
 * @author mkucera
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PostAnalysisCutoffTest extends BaseNetworkTest {

	private static final String PATH = "src/test/resources/org/baderlab/csplugins/enrichmentmap/task/EMandPA/";
	
	private static CyNetwork emNetwork;
	
	private PostAnalysisParameters getPaParams() {
		PostAnalysisParameters paParams = new PostAnalysisParameters();
    	paParams.setSignature_dataSet(EnrichmentMap.DATASET1);
    	paParams.setSignature_rankFile(EnrichmentMap.DATASET1);
		paParams.setKnownSignature(true);
		paParams.setSignatureHub(false);
		paParams.setUniverseSize(11445);
		paParams.setSignatureGMTFileName(PATH + "PA_top8_middle8_bottom8.gmt");
		return paParams;
	}
	
	
	@Test
	public void _setup() {
		EnrichmentMapParameters emParams = new EnrichmentMapParameters(sessionManager, streamUtil, applicationManager);
		emParams.setMethod(EnrichmentMapParameters.method_generic);
		DataSetFiles dataset1files = new DataSetFiles();
		dataset1files.setGMTFileName(PATH + "gene_sets.gmt");  
		dataset1files.setExpressionFileName(PATH + "FakeExpression.txt");
		dataset1files.setEnrichmentFileName1(PATH + "fakeEnrichments.txt");
		dataset1files.setRankedFile(PATH + "FakeRank.rnk");  
		emParams.addFiles(EnrichmentMap.DATASET1, dataset1files);
		
	    buildEnrichmentMap(emParams);
	    
	    // Assert the network is as expected
	   	Set<CyNetwork> networks = networkManager.getNetworkSet();
	   	assertEquals(1, networks.size());
	   	emNetwork = networks.iterator().next();
	}
	 
	
	@Test
	public void test_1_FilterType_Number() throws Exception {
		PostAnalysisParameters paParams = getPaParams();
		
		paParams.getRankTestParameters().setType(FilterType.NUMBER);
		paParams.getRankTestParameters().setValue(FilterType.NUMBER, 5);
		
		runPostAnalysis(emNetwork, paParams);
	   	
	   	Map<String,CyEdge> edges = getEdges(emNetwork);
	   	assertEquals(9, edges.size());
	   	
	   	CyEdge edge = edges.get("PA_TOP8_MIDDLE8_BOTTOM8 (sig) MIDDLE8_PLUS100");
	   	assertNotNull(edge);
	   	assertEquals(8, emNetwork.getRow(edge).get("EM1_k_intersection", Integer.class).intValue());
	   	assertEquals(FilterType.NUMBER.toString(), emNetwork.getRow(edge).get("EM1_Overlap_cutoff", String.class));
	   	
	   	edge = edges.get("PA_TOP8_MIDDLE8_BOTTOM8 (sig) BOTTOM8_PLUS100");
	   	assertNotNull(edge);
	   	assertEquals(8, emNetwork.getRow(edge).get("EM1_k_intersection", Integer.class).intValue());
	   	assertEquals(FilterType.NUMBER.toString(), emNetwork.getRow(edge).get("EM1_Overlap_cutoff", String.class));
	   	
	   	edge = edges.get("PA_TOP8_MIDDLE8_BOTTOM8 (sig) TOP8_PLUS100");
	   	assertNotNull(edge);
	   	assertEquals(8, emNetwork.getRow(edge).get("EM1_k_intersection", Integer.class).intValue());
	   	assertEquals(FilterType.NUMBER.toString(), emNetwork.getRow(edge).get("EM1_Overlap_cutoff", String.class));
	   	
	   	edge = edges.get("PA_TOP8_MIDDLE8_BOTTOM8 (sig) TOP1_PLUS100");
	   	assertNull(edge);
	}
	
	@Test
	public void test_2_FilterType_Percent() throws Exception {
		PostAnalysisParameters paParams = getPaParams();
		
		paParams.getRankTestParameters().setType(FilterType.PERCENT);
		paParams.getRankTestParameters().setValue(FilterType.PERCENT, 7);
		
		runPostAnalysis(emNetwork, paParams);
		
	   	Map<String,CyEdge> edges = getEdges(emNetwork);
	   	assertEquals(9, edges.size());
	   	
	   	CyEdge edge = edges.get("PA_TOP8_MIDDLE8_BOTTOM8 (sig) MIDDLE8_PLUS100");
	   	assertNotNull(edge);
	   	assertEquals(8, emNetwork.getRow(edge).get("EM1_k_intersection", Integer.class).intValue());
	   	assertEquals(FilterType.PERCENT.toString(), emNetwork.getRow(edge).get("EM1_Overlap_cutoff", String.class));
	   	
	   	edge = edges.get("PA_TOP8_MIDDLE8_BOTTOM8 (sig) BOTTOM8_PLUS100");
	   	assertNotNull(edge);
	   	assertEquals(8, emNetwork.getRow(edge).get("EM1_k_intersection", Integer.class).intValue());
	   	assertEquals(FilterType.PERCENT.toString(), emNetwork.getRow(edge).get("EM1_Overlap_cutoff", String.class));
	   	
	   	edge = edges.get("PA_TOP8_MIDDLE8_BOTTOM8 (sig) TOP8_PLUS100");
	   	assertNotNull(edge);
	   	assertEquals(8, emNetwork.getRow(edge).get("EM1_k_intersection", Integer.class).intValue());
	   	assertEquals(FilterType.PERCENT.toString(), emNetwork.getRow(edge).get("EM1_Overlap_cutoff", String.class));
	   	
	   	edge = edges.get("PA_TOP8_MIDDLE8_BOTTOM8 (sig) TOP1_PLUS100");
	   	assertNull(edge);
	}
	
	@Test
	public void test_3_FilterType_Specific() throws Exception {
		PostAnalysisParameters paParams = getPaParams();
		
		paParams.getRankTestParameters().setType(FilterType.SPECIFIC);
		paParams.getRankTestParameters().setValue(FilterType.SPECIFIC, 25);
		
		runPostAnalysis(emNetwork, paParams);
		
	   	Map<String,CyEdge> edges = getEdges(emNetwork);
	   	assertEquals(9, edges.size());
	   	
	   	CyEdge edge = edges.get("PA_TOP8_MIDDLE8_BOTTOM8 (sig) MIDDLE8_PLUS100");
	   	assertNotNull(edge);
	   	assertEquals(8, emNetwork.getRow(edge).get("EM1_k_intersection", Integer.class).intValue());
	   	assertEquals(FilterType.SPECIFIC.toString(), emNetwork.getRow(edge).get("EM1_Overlap_cutoff", String.class));
	   	
	   	edge = edges.get("PA_TOP8_MIDDLE8_BOTTOM8 (sig) BOTTOM8_PLUS100");
	   	assertNotNull(edge);
	   	assertEquals(8, emNetwork.getRow(edge).get("EM1_k_intersection", Integer.class).intValue());
	   	assertEquals(FilterType.SPECIFIC.toString(), emNetwork.getRow(edge).get("EM1_Overlap_cutoff", String.class));
	   	
	   	edge = edges.get("PA_TOP8_MIDDLE8_BOTTOM8 (sig) TOP8_PLUS100");
	   	assertNotNull(edge);
	   	assertEquals(8, emNetwork.getRow(edge).get("EM1_k_intersection", Integer.class).intValue());
	   	assertEquals(FilterType.SPECIFIC.toString(), emNetwork.getRow(edge).get("EM1_Overlap_cutoff", String.class));
	   	
	   	edge = edges.get("PA_TOP8_MIDDLE8_BOTTOM8 (sig) TOP1_PLUS100");
	   	assertNull(edge);
	}
}