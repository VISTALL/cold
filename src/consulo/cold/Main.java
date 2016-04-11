package consulo.cold;

import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.UserDataHolderBase;
import consulo.cold.execute.ExecuteFailedException;
import consulo.cold.execute.ExecuteIndicator;
import consulo.cold.execute.ExecuteTarget;
import consulo.cold.execute.target.PrepareDependenciesTarget;
import consulo.cold.execute.target.PrepareEnvironmentTarget;
import consulo.cold.util.ColdLoggerFactory;

/**
 * @author VISTALL
 * @since 11.04.2016
 */
public class Main
{
	public static void main(String[] args) throws Exception
	{
		Logger.setFactory(ColdLoggerFactory.class);

		ExecuteTarget[] targets = new ExecuteTarget[]{
				new PrepareEnvironmentTarget(),
				new PrepareDependenciesTarget()
		};

		ExecuteIndicator indicator = new ExecuteIndicator()
		{
			@Override
			public void setText(@Nullable String text)
			{
				System.out.println(text);
			}
		};


		UserDataHolderBase context = new UserDataHolderBase();

		for(ExecuteTarget target : targets)
		{
			try
			{
				target.execute(indicator, context);
			}
			catch(ExecuteFailedException e)
			{
				e.printStackTrace();

				break;
			}
		}
	}
}
