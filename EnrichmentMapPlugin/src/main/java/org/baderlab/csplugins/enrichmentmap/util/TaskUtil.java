package org.baderlab.csplugins.enrichmentmap.util;

import java.util.function.Consumer;

import org.cytoscape.work.FinishStatus;
import org.cytoscape.work.FinishStatus.Type;
import org.cytoscape.work.ObservableTask;
import org.cytoscape.work.TaskObserver;

public class TaskUtil {
	private TaskUtil() {}
	
	public static TaskObserver onFail(Consumer<FinishStatus> consumer) {
		return new TaskObserver() {
			@Override
			public void taskFinished(ObservableTask task) { }
			@Override
			public void allFinished(FinishStatus finishStatus) {
				if(finishStatus.getType() == Type.FAILED) {
					consumer.accept(finishStatus);
				}
			}
		};
	}
	
	public static <T> TaskObserver allFinished(Consumer<FinishStatus> consumer) {
		return new TaskObserver() {
			@Override
			public void taskFinished(ObservableTask task) {
			}

			@Override
			public void allFinished(FinishStatus finishStatus) { 
				consumer.accept(finishStatus);
			}
		};
	}
	
	public static <T> TaskObserver taskFinished(Consumer<ObservableTask> consumer) {
		return new TaskObserver() {
			@Override
			public void taskFinished(ObservableTask task) {
				consumer.accept(task);
			}

			@Override
			public void allFinished(FinishStatus finishStatus) { }
		};
	}
	
	public static <T> TaskObserver taskFinished(Class<T> taskType, Consumer<T> consumer) {
		return new TaskObserver() {
			@Override
			public void taskFinished(ObservableTask task) {
				if(taskType.isInstance(task)) {
					consumer.accept(taskType.cast(task));
				}
			}

			@Override
			public void allFinished(FinishStatus finishStatus) { }
		};
	}

}
