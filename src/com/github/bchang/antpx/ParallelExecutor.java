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
@SuppressWarnings({"UnusedDeclaration"})
public class ParallelExecutor implements Executor {

    public static final int DEFAULT_NUM_THREADS = 1;
    public static final int DEFAULT_TIMEOUT = 600;
    public static final String NUMTHREADS_PROPKEY = "antpx.numthreads";
    public static final String DEBUG_PROPKEY = "antpx.debug";
    public static final String TIMEOUT_PROPKEY = "antpx.timeout";

    private static final SingleCheckExecutor SUB_EXECUTOR = new SingleCheckExecutor();

    private Deque<String> userTargets;
    private LinkedList<Target> targetsYetToRun = new LinkedList<Target>();
    private HashSet<String> targetsCompleted;
    private FailedTarget failedTarget;

    private boolean debug;
    private ExecutorService executorService;
    private Project project;

    public Executor getSubProjectExecutor() {
        return SUB_EXECUTOR;
    }

    public void executeTargets(Project project, String[] targetNames) throws BuildException {
        this.project = project;

        debug = "true".equals(project.getProperty(DEBUG_PROPKEY));
        int numThreads = readIntProp(project, DEFAULT_NUM_THREADS, NUMTHREADS_PROPKEY);
        int timeout = readIntProp(project, DEFAULT_TIMEOUT, TIMEOUT_PROPKEY);

        project.log(getClass().getName() + " with " + numThreads + " threads, timeout " + timeout + " sec");
        executorService = Executors.newFixedThreadPool(numThreads);

        userTargets = new LinkedList<String>(Arrays.asList(targetNames));
        loadReadyTargets();

        try {
            boolean terminated = executorService.awaitTermination(timeout, TimeUnit.SECONDS);

            if (!terminated) {
              throw new BuildException("parallel target execution timed out at " + timeout + " seconds");
            }
        }
        catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        synchronized(this) {
            if (failedTarget != null) {
                throw failedTarget.ex;
            }
        }
    }

    private synchronized void targetCompleted(String targetName) {
        if (debug) {
          project.log("TargetQueue: completed target " + targetName);
        }
        targetsCompleted.add(targetName);
        loadReadyTargets();
    }

    private synchronized void loadReadyTargets() {
        if (targetsYetToRun.size() == 0) {
            String userTargetName = userTargets.poll();
            if (userTargetName == null) {
                executorService.shutdown();
            }
            else {
                @SuppressWarnings({"unchecked"})
                Vector<Target> topoSort = (Vector<Target>) project.topoSort(userTargetName, project.getTargets(), false);
                targetsYetToRun = new LinkedList<Target>(topoSort);
                targetsCompleted = new HashSet<String>();
            }
        }
        for (Iterator<Target> it = targetsYetToRun.iterator(); it.hasNext();) {
            Target target = it.next();
            if (isTargetReady(target, targetsCompleted)) {
                it.remove();
                if (debug) {
                    project.log("TargetQueue: submitting target " + target.getName());
                }
                executorService.submit(new TargetJob(target));
            }
        }
    }

    private synchronized void targetFailed(String targetName, RuntimeException ex) {
        if (failedTarget == null) {
            failedTarget = new FailedTarget(targetName, ex);
            executorService.shutdown();
        }
        else {
            // ignore
        }
    }

    private synchronized boolean hasATargetFailed() {
        return failedTarget != null;
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

    private static int readIntProp(Project project, int defaultVal, String propKey) {
        String prop = project.getProperty(propKey);
        return prop == null ? defaultVal : Integer.parseInt(prop);
    }

    private class TargetJob implements Runnable {
        private Target target;

        TargetJob(Target target) {
            this.target = target;
        }

        public void run() {
            if (!hasATargetFailed()) {
                try {
                    target.performTasks();
                    targetCompleted(target.getName());
                } catch (RuntimeException ex) {
                    targetFailed(target.getName(), ex);
                }
            }
        }
    }

    private static class FailedTarget {
        String targetName;
        RuntimeException ex;

        private FailedTarget(String targetName, RuntimeException ex) {
            this.targetName = targetName;
            this.ex = ex;
        }
    }
}
