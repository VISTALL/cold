package consulo.cold.execute;

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

	void execute(@NotNull ExecuteIndicator executeIndicator, @NotNull UserDataHolder executeContext) throws ExecuteFailedException;
}
