package consulo.cold.execute.target;

import java.io.File;
import java.io.IOException;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import consulo.cold.execute.ExecuteFailedException;
import consulo.cold.execute.ExecuteIndicator;
import consulo.cold.execute.ExecuteTarget;

/**
 * @author VISTALL
 * @since 12.04.2016
 */
public class PrepareEnvironmentTarget implements ExecuteTarget
{
	@Override
	public void execute(@NotNull ExecuteIndicator executeIndicator, @NotNull UserDataHolder executeContext) throws ExecuteFailedException
	{
		File consuloDir = new File(".", ".consulo");
		if(!consuloDir.exists())
		{
			throw new ExecuteFailedException(consuloDir.getParentFile().getAbsolutePath() + " is not Consulo project");
		}

		try
		{
			String name = FileUtil.loadFile(new File(consuloDir, ".name"), CharsetToolkit.UTF8);

			executeContext.putUserData(WORKING_DIRECTORY, consuloDir.getParentFile());
			executeContext.putUserData(PROJECT_NAME, name);
		}
		catch(IOException e)
		{
			throw new ExecuteFailedException(e);
		}
	}
}
