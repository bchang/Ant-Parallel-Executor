package com.github.bchang.antpx;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Executor;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Target;
import org.apache.tools.ant.helper.SingleCheckExecutor;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 */
public class ParallelExecutor implements Executor {

    public static final int DEFAULT_NUM_THREADS = 1;
    public static final String NUMTHREADS_PROPKEY = "antpx.numthreads";

    private static final SingleCheckExecutor SUB_EXECUTOR = new SingleCheckExecutor();

    private HashSet<String> targetsCompleted = new HashSet<String>();
    private LinkedList<Target> targetsYetToRun;
    ExecutorService executorService;

    public Executor getSubProjectExecutor() {
        return SUB_EXECUTOR;
    }

    public void executeTargets(Project project, String[] targetNames) throws BuildException {
        int numThreads = DEFAULT_NUM_THREADS;
        String propNumThreads = project.getProperty(NUMTHREADS_PROPKEY);
        if (propNumThreads != null) {
            numThreads = Integer.parseInt(propNumThreads);
        }
        project.log(getClass().getName() + " with " + numThreads + " threads");
        executorService = Executors.newFixedThreadPool(numThreads);

        @SuppressWarnings({"unchecked"})
        Vector<Target> topoSort = (Vector<Target>) project.topoSort(targetNames, project.getTargets(), false);
        targetsYetToRun = new LinkedList<Target>(topoSort);
        loadReadyTargets();

        try {
            executorService.awaitTermination(600, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private synchronized void targetCompleted(String targetName) {
        targetsCompleted.add(targetName);
        loadReadyTargets();
    }

    private synchronized void loadReadyTargets() {
        if (targetsYetToRun.size() == 0) {
            executorService.shutdown();
        }
        for (Iterator<Target> it = targetsYetToRun.iterator(); it.hasNext();) {
            Target target = it.next();
            if (isTargetReady(target, targetsCompleted)) {
                it.remove();
                executorService.submit(new TargetJob(target));
            }
        }
    }

    private static boolean isTargetReady(Target target, HashSet<String> completed) {
        for (Enumeration e = target.getDependencies(); e.hasMoreElements();) {
            String dependency = (String) e.nextElement();
            if (!completed.contains(dependency)) {
                return false;
            }
        }
        return true;
    }

    class TargetJob implements Runnable {
        private Target target;

        TargetJob(Target target) {
            this.target = target;
        }

        public void run() {
            try {
                target.performTasks();
            } catch (BuildException e) {
                // TODO - probably not quite right
                executorService.shutdownNow();
            }
            targetCompleted(target.getName());
        }
    }
}
