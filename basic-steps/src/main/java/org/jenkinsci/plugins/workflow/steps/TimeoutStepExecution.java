package org.jenkinsci.plugins.workflow.steps;

import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import jenkins.util.Timer;

/**
 * @author Kohsuke Kawaguchi
 */
@SuppressFBWarnings("SE_INNER_CLASS")
public class TimeoutStepExecution extends AbstractStepExecutionImpl {
    @Inject(optional=true)
    private TimeoutStep step;
    private BodyExecution body;
    private transient ScheduledFuture<?> killer;

    private long end;

    @Override
    public boolean start() throws Exception {
        StepContext context = getContext();
        body = context.newBodyInvoker()
                .withCallback(new Callback())
                .withDisplayName(null)  // hide the body block
                .start();
        long now = step.getUnit().convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        end = now + step.getTime();
        setupTimer(now);
        return false;   // execution is asynchronous
    }

    @Override
    public void onResume() {
        super.onResume();
        setupTimer(step.getUnit().convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS));
    }

    private void setupTimer(final long now) {
        if (end > now) {
            killer = Timer.get().schedule(new Runnable() {
                @Override
                public void run() {
                    if (!body.isDone()) {
                        // TODO use a proper CauseOfInterruption
                        body.cancel(true);
                    }
                }
            }, end - now, step.getUnit());
        } else {
            getContext().onFailure(null);
        }
    }

    @Override
    public void stop(Throwable cause) throws Exception {
        if (body!=null)
            body.cancel(cause);
    }

    private class Callback extends BodyExecutionCallback {
        @Override
        public void onSuccess(StepContext context, Object result) {
            cancelKiller();
            getContext().onSuccess(result);
        }

        @Override
        public void onFailure(StepContext context, Throwable t) {
            cancelKiller();
            getContext().onFailure(t);
        }

        private void cancelKiller() {
            if (killer!=null) {
                killer.cancel(true);
                killer = null;
            }
        }

        private static final long serialVersionUID = 1L;
    }

    private static final long serialVersionUID = 1L;
}
