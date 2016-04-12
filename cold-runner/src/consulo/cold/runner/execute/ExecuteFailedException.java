package consulo.cold.runner.execute;

/**
 * @author VISTALL
 * @since 11.04.2016
 */
public class ExecuteFailedException extends Exception
{
	public ExecuteFailedException()
	{
	}

	public ExecuteFailedException(String message)
	{
		super(message);
	}

	public ExecuteFailedException(String message, Throwable cause)
	{
		super(message, cause);
	}

	public ExecuteFailedException(Throwable cause)
	{
		super(cause);
	}
}
