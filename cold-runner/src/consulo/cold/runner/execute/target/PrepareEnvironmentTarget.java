package consulo.cold.runner.execute.target;

import java.io.File;
import java.io.IOException;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import consulo.cold.runner.execute.ExecuteFailedException;
import consulo.cold.runner.execute.ExecuteLogger;
import consulo.cold.runner.execute.ExecuteTarget;

/**
 * @author VISTALL
 * @since 12.04.2016
 */
public class PrepareEnvironmentTarget implements ExecuteTarget
{
	@Override
	public void execute(@NotNull ExecuteLogger executeLogger, @NotNull UserDataHolder executeContext) throws ExecuteFailedException
	{
		File consuloDir = new File(".", ".consulo");
		File parentFile = consuloDir.getParentFile();

		if(!consuloDir.exists())
		{
			throw new ExecuteFailedException(parentFile.getAbsolutePath() + " is not Consulo project");
		}

		executeLogger.info("Dropping out directory");
		FileUtil.delete(new File(parentFile, "out"));

		try
		{
			File nameFile = new File(consuloDir, ".name");

			String name = nameFile.exists() ? FileUtil.loadFile(nameFile, CharsetToolkit.UTF8) : parentFile.getCanonicalFile().getName();

			executeLogger.info("Project name: " + name);
			
			executeContext.putUserData(WORKING_DIRECTORY, parentFile);
			executeContext.putUserData(PROJECT_NAME, name);
		}
		catch(IOException e)
		{
			throw new ExecuteFailedException(e);
		}
	}
}
