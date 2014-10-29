package org.baderlab.csplugins.enrichmentmap.autoannotate.task.cluster;

import java.util.ArrayList;
import java.util.HashSet;

import javax.swing.JOptionPane;
import javax.swing.JTable;

import org.baderlab.csplugins.enrichmentmap.autoannotate.AutoAnnotationManager;
import org.baderlab.csplugins.enrichmentmap.autoannotate.model.AnnotationSet;
import org.baderlab.csplugins.enrichmentmap.autoannotate.model.Cluster;
import org.baderlab.csplugins.enrichmentmap.autoannotate.task.Observer;
import org.baderlab.csplugins.enrichmentmap.autoannotate.task.UpdateAnnotationTask;
import org.baderlab.csplugins.enrichmentmap.autoannotate.task.VisualizeClusterAnnotationTaskFactory;
import org.cytoscape.application.swing.CytoPanel;
import org.cytoscape.command.CommandExecutorTaskFactory;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.CyTableManager;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.View;
import org.cytoscape.view.presentation.property.BasicVisualLexicon;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.TaskIterator;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.swing.DialogTaskManager;

public class ExtractClusterTask extends AbstractTask{

	private AnnotationSet annotationSet;
	
	
	
	public ExtractClusterTask(AnnotationSet annotationSet) {
		super();
		this.annotationSet = annotationSet;
	}

	public void extractAction() {
		CyNetworkView selectedView = annotationSet.getView();
		CyNetwork selectedNetwork = selectedView.getModel();
		AutoAnnotationManager autoAnnotationManager = AutoAnnotationManager.getInstance();
		 JTable clusterTable = annotationSet.getClusterTable();
		 
		// Get selected nodes
		ArrayList<CyNode> selectedNodes = new ArrayList<CyNode>();
		for (CyNode node : selectedNetwork.getNodeList()) {
			if (selectedNetwork.getRow(node).get(CyNetwork.SELECTED, Boolean.class)) {
				selectedNodes.add(node);
			}
		}
		// Clear node selections (for WordCloud)
		for (CyNode node : selectedNodes) {
			selectedNetwork.getRow(node).set(CyNetwork.SELECTED, false);
		}
		if (selectedNodes.size() < 1) {
			JOptionPane.showMessageDialog(null, "Please select at least one node", "Error Message",
					JOptionPane.ERROR_MESSAGE);
		} else {
			// Get services needed for accessing WordCloud through command line
			CommandExecutorTaskFactory executor = autoAnnotationManager.getCommandExecutor();
			DialogTaskManager dialogTaskManager = autoAnnotationManager.getDialogTaskManager();
			Class<?> columnType = selectedNetwork.getDefaultNodeTable().getColumn(annotationSet.getClusterColumnName()).getType();
			String clusterColumnName = annotationSet.getClusterColumnName();
			String nameColumnName = annotationSet.getNameColumnName();
			int newClusterNumber = annotationSet.getNextClusterNumber();
			Cluster newCluster = null;
			
			if (columnType == Integer.class) {
				// Discrete clusters, remove nodes from other clusters
				HashSet<Cluster> clustersChanged = new HashSet<Cluster>();
				for (Cluster cluster : annotationSet.getClusterMap().values()) {
					for (CyNode node : selectedNodes) {
						if (cluster.getNodes().contains(node)) {
							cluster.removeNode(node);
							cluster.setCoordinatesChanged(true);
							clustersChanged.add(cluster);
						}
					}
				}
				for (CyRow row : selectedNetwork.getDefaultNodeTable().getAllRows()) {
					row.set(clusterColumnName, newClusterNumber);
				}
				for (Cluster modifiedCluster : clustersChanged) {
					// Select nodes to make the cloud
					for (CyNode node : modifiedCluster.getNodesToCoordinates().keySet()) {
						selectedNetwork.getRow(node).set(CyNetwork.SELECTED, true);
					}
					// Create a new cloud for the extracted cluster
					ArrayList<String> commands = new ArrayList<String>();
					String deleteCommand = "wordcloud delete cloudName=\"" +  modifiedCluster.getCloudName() + "\"";
					String createCommand = "wordcloud create wordColumnName=\"" + nameColumnName + "\"" + 
							" nodeList=\"selected\" cloudName=\"" +  modifiedCluster.getCloudName() + "\""
							+ " cloudGroupTableName=\"" + annotationSet.getName() + "\"";
					commands.add(deleteCommand);
					commands.add(createCommand);
					Observer observer = new Observer();
					TaskIterator taskIterator = executor.createTaskIterator(commands, null);
					dialogTaskManager.execute(taskIterator, observer);
					// Wait until WordCloud is finished
					while (!observer.isFinished()) {
						try {
							Thread.sleep(1);
						} catch (InterruptedException e1) {
							e1.printStackTrace();
						}
					}
					// Clear selection
					for (CyNode node : modifiedCluster.getNodesToCoordinates().keySet()) {
						selectedNetwork.getRow(node).set(CyNetwork.SELECTED, false);
					}
					// Ensures that only one cluster changes at a time in the table
					autoAnnotationManager.flushPayloadEvents();
				}
			}
			
			newCluster = new Cluster(newClusterNumber, annotationSet);
			annotationSet.addCluster(newCluster);
			
			for (CyNode node : selectedNodes) {
				// Get coordinates from the nodeView
				View<CyNode> nodeView = selectedView.getNodeView(node);
				double x = nodeView.getVisualProperty(BasicVisualLexicon.NODE_X_LOCATION);
				double y = nodeView.getVisualProperty(BasicVisualLexicon.NODE_Y_LOCATION);
				double[] coordinates = {x, y};
				double nodeRadius = nodeView.getVisualProperty(BasicVisualLexicon.NODE_WIDTH);
				newCluster.addNodeCoordinates(node, coordinates);
				newCluster.addNodeRadius(node, nodeRadius);
				selectedNetwork.getRow(node).set(CyNetwork.SELECTED, true);
			}	

			for (CyNode node : newCluster.getNodesToCoordinates().keySet()) {
				selectedNetwork.getRow(node).set(CyNetwork.SELECTED, true);
			}
			
			// Create a new cloud for the extracted cluster
			ArrayList<String> commands = new ArrayList<String>();
			String command = "wordcloud create wordColumnName=\"" + nameColumnName + "\"" + 
					" nodeList=\"selected\" cloudName=\"" +  newCluster.getCloudName() + "\""
					+ " cloudGroupTableName=\"" + annotationSet.getName() + "\"";
			commands.add(command);
			Observer observer = new Observer();
			TaskIterator taskIterator = executor.createTaskIterator(commands, null);
			dialogTaskManager.execute(taskIterator, observer);
			// Wait until WordCloud is finished
			while (!observer.isFinished()) {
				try {
					Thread.sleep(1);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
			}
			for (CyNode node : selectedNodes) {
				selectedNetwork.getRow(node).set(CyNetwork.SELECTED, false);
			}

			CyTableManager tableManager = autoAnnotationManager.getTableManager();
			
			String annotationSetName = annotationSet.getName();
			Long clusterTableSUID = selectedNetwork.getDefaultNetworkTable().getRow(selectedNetwork.getSUID()).get(annotationSetName, Long.class);
	    	CyTable clusterSetTable = tableManager.getTable(clusterTableSUID);
	    	
	    	// Generate the labels for the clusters
	    	this.insertTasksAfterCurrentTask(new UpdateClusterLabelTask(newCluster,clusterSetTable));

			
			// Redraw selected clusters
	    	this.insertTasksAfterCurrentTask(new VisualizeClusterAnnotationTaskFactory(newCluster).createTaskIterator());
			
			this.insertTasksAfterCurrentTask(new UpdateAnnotationTask(annotationSet));
		
			// Deselect rows (no longer meaningful)
			clusterTable.clearSelection();
			// Focus on this panel
			CytoPanel westPanel = autoAnnotationManager.getWestPanel();
			westPanel.setSelectedIndex(westPanel.indexOfComponent(autoAnnotationManager.getAnnotationPanel()));
		}
	}
	
	@Override
	public void run(TaskMonitor arg0) throws Exception {
		extractAction();
		
	}
	
	

}