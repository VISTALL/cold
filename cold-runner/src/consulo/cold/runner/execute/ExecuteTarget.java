package consulo.cold.runner.execute;

import java.io.File;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;

/**
 * @author VISTALL
 * @since 11.04.2016
 */
public interface ExecuteTarget
{
	Key<File> WORKING_DIRECTORY = Key.create("working.directory");

	Key<Project> PROJECT = Key.create("project");

	Key<String> PROJECT_NAME = Key.create("project.name");

	void execute(@NotNull ExecuteLogger executeLogger, @NotNull UserDataHolder executeContext) throws ExecuteFailedException;
}
