package consulo.cold.runner.execute;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author VISTALL
 * @since 11.04.2016
 */
public class ExecuteLogger
{
	public void info(@Nullable String text)
	{
		System.out.println(text);
	}

	public void warn(@NotNull Exception e)
	{
		warn(e.getMessage(), e);
	}

	public void warn(@NotNull String message, @Nullable Exception e)
	{
		System.out.println(message);
		e.printStackTrace();
	}
}
