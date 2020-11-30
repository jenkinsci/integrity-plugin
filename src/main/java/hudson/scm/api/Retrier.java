package hudson.scm.api;

/**
 * This class is implemented to handle an exception o reaching maximum number of retries
 */
public class Retrier {
    public static final int MAX_RETRIES = 5;
    public static final long TIME_TO_WAIT_MS = 1000;

    private int maxRetries;
    private long timeToWaitMS;

    // CONSTRUCTORS
    public Retrier(int argMaxRetries, long argTimeToWaitMS)
    {
    	maxRetries = argMaxRetries;
        timeToWaitMS = argTimeToWaitMS;
    }

    public Retrier()
    {
        this(MAX_RETRIES, TIME_TO_WAIT_MS);
    }

    /**
     * Returns true if a retry can be attempted.
     * @return true if retries attempts remain 
     *         else false
     */
    public boolean shouldRetry()
    {
        return (maxRetries >= 0);
    }

    /**
     * Waits for timeToWaitMS. 
     * Ignores any interrupted exception
     */
    public void waitUntilNextTry()
    {
        try {
            Thread.sleep(timeToWaitMS);
        }
        catch (InterruptedException iex) { }
    }

    /**
     * Call when an exception has occurred in the block. If the
     * retry limit is exceeded, throws an exception.
     * Else waits for the specified time.
     * 
     * @throws Exception
     */
    public void exceptionOccurred() throws InterruptedException
    {
        maxRetries--;
        if(!shouldRetry())
        {
            throw new InterruptedException("Retry limit exceeded.");
        }
        waitUntilNextTry();
    }
}
