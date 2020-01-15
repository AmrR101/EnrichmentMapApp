package org.baderlab.csplugins.enrichmentmap.commands;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.util.Arrays;
import java.util.List;

import org.baderlab.csplugins.enrichmentmap.commands.tunables.FilterTunables;
import org.baderlab.csplugins.enrichmentmap.model.DataSetParameters;
import org.baderlab.csplugins.enrichmentmap.model.EMCreationParameters;
import org.baderlab.csplugins.enrichmentmap.resolver.ResolverTask;
import org.baderlab.csplugins.enrichmentmap.task.CreateEMNetworkTask;
import org.baderlab.csplugins.enrichmentmap.task.CreateEnrichmentMapTaskFactory;
import org.baderlab.csplugins.enrichmentmap.util.NullTaskMonitor;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.ContainsTunables;
import org.cytoscape.work.FinishStatus;
import org.cytoscape.work.ObservableTask;
import org.cytoscape.work.SynchronousTaskManager;
import org.cytoscape.work.TaskIterator;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.TaskObserver;
import org.cytoscape.work.Tunable;

import com.google.inject.Inject;


public class MastermapCommandTask extends AbstractTask implements ObservableTask {

	@Tunable(required=true, description="Absolute path to a folder containing the data files. "
			+ "The files will be scanned and automatically grouped into data sets. Sub-folders will be scanned up to one level deep.")
	public File rootFolder;
	
	@Tunable(description="Absolute path to a GMT file that will be used for every data set. Overrides other GMT files.")
	public File commonGMTFile;
	
	@Tunable(description="Absolute path to an expression file that will be used for every data set. Overrides other expression files.")
	public File commonExpressionFile;
	
	@ContainsTunables
	@Inject
	public FilterTunables filterArgs;
	
	@Tunable(description="A glob-style path filter. Sub-folders inside the root folder that do not match the pattern will be ignored. "
			+ "For more details on syntax see https://docs.oracle.com/javase/8/docs/api/java/nio/file/FileSystem.html#getPathMatcher-java.lang.String-")
	public String pattern;
	
	@Inject private SynchronousTaskManager<?> taskManager;
	@Inject private CreateEnrichmentMapTaskFactory.Factory taskFactoryFactory;
	
	
	private Long[] result = { null };
	
	
	@Override
	public void run(TaskMonitor tm) throws Exception {
		if(tm == null)
			tm = new NullTaskMonitor();
		tm.setStatusMessage("Running EnrichmentMap Mastermap Task");
		
		if(rootFolder == null || !rootFolder.exists()) {
			throw new IllegalArgumentException("rootFolder is invalid: " + rootFolder);
		}
		
		// Scan root folder (note: throws exception if no data sets were found)
		ResolverTask resolverTask = new ResolverTask(rootFolder);
		
		if(pattern != null) {
			PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
			resolverTask.setPathMatcher(matcher);
		}
		
		taskManager.execute(new TaskIterator(resolverTask)); // blocks
		List<DataSetParameters> dataSets = resolverTask.getDataSetResults();
		
		tm.setStatusMessage("resolved " + dataSets.size() + " data sets");
		for(DataSetParameters params : dataSets) {
			tm.setStatusMessage(params.toString());
		}
		
		// Common gmt and expression files
		// Overwrite all the expression files if the common file has been provided
		if(commonExpressionFile != null) {
			if(!commonExpressionFile.canRead()) {
				throw new IllegalArgumentException("Cannot read commonExpressionFile: " + commonExpressionFile);
			}
			for(DataSetParameters dsp : dataSets) {
				dsp.getFiles().setExpressionFileName(commonExpressionFile.getAbsolutePath());
			}
		}
		
		// Overwrite all the gmt files if a common file has been provided
		if(commonGMTFile != null) {
			if(!commonGMTFile.canRead()) {
				throw new IllegalArgumentException("Cannot read commonGMTFile: " + commonGMTFile);
			}
			for(DataSetParameters dsp : dataSets) {
				dsp.getFiles().setGMTFileName(commonGMTFile.getAbsolutePath());
			}
		}
		
		tm.setStatusMessage(filterArgs.toString());
		
		EMCreationParameters params = filterArgs.getCreationParameters();

		if(filterArgs.networkName != null && !filterArgs.networkName.trim().isEmpty()) {
			params.setNetworkName(filterArgs.networkName);
		}
		
		CreateEnrichmentMapTaskFactory taskFactory = taskFactoryFactory.create(params, dataSets);
		TaskIterator tasks = taskFactory.createTaskIterator();
		
		taskManager.execute(tasks, new TaskObserver() {
			@Override 
			public void taskFinished(ObservableTask task) {
				if(task instanceof CreateEMNetworkTask) {
					CreateEMNetworkTask networkTask = (CreateEMNetworkTask) task;
					result[0] = networkTask.getResults(Long.class);
				}
			}
			@Override 
			public void allFinished(FinishStatus finishStatus) { }
		});
		
		tm.setStatusMessage("Done.");
	}
	
	@Override
	public List<Class<?>> getResultClasses() {
		return Arrays.asList(String.class, Long.class);
	}
	
	@Override
	public <R> R getResults(Class<? extends R> type) {
		if(String.class.equals(type)) {
			return type.cast(String.valueOf(result[0]));
		}
		if(Long.class.equals(type)) {
			return type.cast(result[0]);
		}
		return null;
	}
	
}