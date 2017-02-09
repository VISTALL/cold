package consulo.cold.runner.execute;

import java.io.File;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;

/**
 * @author VISTALL
 * @since 11.04.2016
 */
public interface ExecuteTarget
{
	Key<File> WORKING_DIRECTORY = Key.create("working.directory");

	Key<String> PROJECT_NAME = Key.create("project.name");

	Key<String> BUILD_NUMBER = Key.create("build.number");

	void execute(@NotNull ExecuteLogger executeLogger, @NotNull UserDataHolder executeContext) throws ExecuteFailedException;
}
