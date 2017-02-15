package org.baderlab.csplugins.enrichmentmap.view.heatmap;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.baderlab.csplugins.enrichmentmap.model.EnrichmentMap;
import org.baderlab.csplugins.enrichmentmap.model.EnrichmentMapManager;
import org.baderlab.csplugins.enrichmentmap.style.EMStyleBuilder;
import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.application.swing.CySwingApplication;
import org.cytoscape.application.swing.CytoPanel;
import org.cytoscape.application.swing.CytoPanelComponent;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTableUtil;
import org.cytoscape.model.events.RowsSetEvent;
import org.cytoscape.model.events.RowsSetListener;
import org.cytoscape.service.util.CyServiceRegistrar;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class HeatMapMediator implements RowsSetListener {

	@Inject private Provider<HeatMapPanel> panelProvider;
	@Inject private EnrichmentMapManager emManager;
	
	@Inject private CyServiceRegistrar serviceRegistrar;
	@Inject private CySwingApplication swingApplication;
	@Inject private CyApplicationManager applicationManager;
	
	
	public void showExpressionViewerPanel() {
		CytoPanelComponent panel = null;
		try {
			panel = serviceRegistrar.getService(CytoPanelComponent.class, "(id=" + HeatMapPanel.ID + ")");
		} catch (Exception ex) { }
		
		if(panel == null) {
			panel = panelProvider.get();
			Properties props = new Properties();
			props.setProperty("id", HeatMapPanel.ID);
			serviceRegistrar.registerService(panel, CytoPanelComponent.class, props);
		}
		
		// Bring panel to frong
		CytoPanel cytoPanel = swingApplication.getCytoPanel(panel.getCytoPanelName());
		int index = cytoPanel.indexOfComponent(HeatMapPanel.ID);
		if(index >= 0)
			cytoPanel.setSelectedIndex(index);
	}

	
	@Override
	public void handleEvent(RowsSetEvent e) {
		// MKTODO If this has bad performance then add a reconciler timer delay.
		// Cytoscape selection events can come in sets of 1-4 events.
		
		CyNetwork network = applicationManager.getCurrentNetwork();

		// only handle event if it is a selected node
		if(network != null && (e.getSource() == network.getDefaultEdgeTable() || e.getSource() == network.getDefaultNodeTable())) {
			final EnrichmentMap map = emManager.getEnrichmentMap(network.getSUID());
			if(map != null) {
				List<CyNode> selectedNodes = CyTableUtil.getNodesInState(network, CyNetwork.SELECTED, true);
				List<CyEdge> selectedEdges = CyTableUtil.getEdgesInState(network, CyNetwork.SELECTED, true);
				
				String prefix = map.getParams().getAttributePrefix();
				
				// For now we are only supporting UNION of genes across selected genesets
				// May also want to support INTERSECTION in the future
				Set<String> allGenes = unionGenesets(network, selectedNodes, selectedEdges, prefix);
				
				panelProvider.get().update(map, allGenes);
			}
		}
	}


	private static Set<String> unionGenesets(CyNetwork network, List<CyNode> nodes, List<CyEdge> edges, String prefix) {
		Set<String> allGenes = new HashSet<>();
		for(CyNode node : nodes) {
			allGenes.addAll(getGenes(network, node, prefix));
		}
		for(CyEdge edge : edges) {
			allGenes.addAll(getGenes(network, edge.getSource(), prefix));
			allGenes.addAll(getGenes(network, edge.getTarget(), prefix));
		}
		return allGenes;
	}
	
	
	private static Collection<String> getGenes(CyNetwork network, CyNode node, String prefix) {
		CyRow row = network.getRow(node);
		// This is already the union of all the genes across data sets
		return EMStyleBuilder.Columns.NODE_GENES.get(row, prefix, null);
	}


	
}