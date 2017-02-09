package consulo.cold.runner;

import java.util.ArrayList;
import java.util.List;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import consulo.cold.runner.execute.ExecuteLogger;
import consulo.cold.runner.execute.ExecuteTarget;
import consulo.cold.runner.execute.target.BuildPluginArtifactsTarget;
import consulo.cold.runner.execute.target.BuildTarget;
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

		List<ExecuteTarget> targets = new ArrayList<>();
		targets.add(new PrepareEnvironmentTarget());
		targets.add(new BuildTarget());
		if(ArrayUtil.contains("--build-plugin-artifacts", args))
		{
			targets.add(new BuildPluginArtifactsTarget());
		}

		ExecuteLogger executeLogger = new ExecuteLogger();

		UserDataHolderBase context = new UserDataHolderBase();

		String buildNumber = System.getProperty("cold.build.number");
		if(!StringUtil.isEmpty(buildNumber))
		{
			context.putUserData(ExecuteTarget.BUILD_NUMBER, buildNumber);
		}
		else
		{
			context.putUserData(ExecuteTarget.BUILD_NUMBER, "SNAPSHOT");
		}

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
