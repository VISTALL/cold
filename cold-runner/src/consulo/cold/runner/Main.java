package consulo.cold.runner;

import java.util.ArrayList;
import java.util.List;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.UserDataHolderBase;
import consulo.cold.runner.execute.ExecuteLogger;
import consulo.cold.runner.execute.ExecuteTarget;
import consulo.cold.runner.execute.target.BuildTarget;
import consulo.cold.runner.execute.target.PrepareDependenciesTarget;
import consulo.cold.runner.execute.target.PrepareEnvironmentTarget;
import consulo.cold.runner.util.ColdLoggerFactory;

/**
 * @author VISTALL
 * @since 11.04.2016
 */
public class Main
{
	public static void main(String[] args) throws Exception
	{
		Logger.setFactory(ColdLoggerFactory.class);

		List<ExecuteTarget> targets = new ArrayList<ExecuteTarget>();
		targets.add(new PrepareEnvironmentTarget());
		targets.add(new PrepareDependenciesTarget());
		targets.add(new BuildTarget());

		ExecuteLogger executeLogger = new ExecuteLogger();

		UserDataHolderBase context = new UserDataHolderBase();

		for(ExecuteTarget target : targets)
		{
			try
			{
				target.execute(executeLogger, context);
			}
			catch(Throwable e)
			{
				e.printStackTrace();
				System.exit(-1);
				return;
			}
		}

		System.exit(0);
	}
}
