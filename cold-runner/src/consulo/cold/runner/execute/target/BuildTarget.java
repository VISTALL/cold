package consulo.cold.runner.execute.target;

import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.Collections;

import org.consulo.compiler.server.application.CompilerServerApplication;
import org.consulo.compiler.server.rmi.CompilerClientConnector;
import org.consulo.compiler.server.rmi.CompilerClientInterface;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.projectRoots.SdkTable;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.impl.SdkImpl;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.impl.local.FileWatcher;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.impl.compiler.ArtifactCompileScope;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.file.impl.FileManagerImpl;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.Semaphore;
import consulo.cold.runner.execute.ExecuteFailedException;
import consulo.cold.runner.execute.ExecuteLogger;
import consulo.cold.runner.execute.ExecuteTarget;

/**
 * @author VISTALL
 * @since 12.04.2016
 */
public class BuildTarget implements ExecuteTarget
{
	@Override
	public void execute(@NotNull final ExecuteLogger executeLogger, @NotNull UserDataHolder executeContext) throws ExecuteFailedException
	{
		String jdk6Home = System.getProperty("jdk6.home");
		if(jdk6Home == null)
		{
			throw new ExecuteFailedException("no 'jdk6.home'");
		}
		String consuloHome = System.getProperty("consulo.home");
		if(consuloHome == null)
		{
			throw new ExecuteFailedException("no 'consulo.home'");
		}

		try
		{
			final File projectDir = executeContext.getUserData(WORKING_DIRECTORY);

			final File tempDataDir = new File(new File(executeContext.getUserData(WORKING_DIRECTORY), ".cold"), "ConsuloData");
			FileUtilRt.createDirectory(tempDataDir);

			System.setProperty(PathManager.PROPERTY_CONFIG_PATH, tempDataDir.getAbsolutePath() + "/config");
			System.setProperty(PathManager.PROPERTY_SYSTEM_PATH, tempDataDir.getAbsolutePath() + "/system");
			System.setProperty(PathManager.PROPERTY_HOME_PATH, consuloHome);
			System.setProperty(FileWatcher.PROPERTY_WATCHER_DISABLED, "true");
			//System.setProperty("vulcan.build.number", buildNumber);
			//System.setProperty("vulcan.consulo.build.number", consuloBuildNumber);

			ApplicationEx app = CompilerServerApplication.createApplication();
			Messages.setTestDialog(new TestDialog()
			{
				@Override
				public int show(String message)
				{
					executeLogger.info(message);
					return 0;
				}
			});

			app.load(PathManager.getOptionsPath());

			setupSdk("JDK", "1.6", jdk6Home);
			//setupSdk("JDK", "1.7", jdk7);
			setupSdk("Consulo Plugin SDK", "Consulo 1.SNAPSHOT", consuloHome);

			/*executeIndicator.setText("Cleanup output directories");

			FileUtil.delete(new File(projectDir, "out"));   */

			executeLogger.info("Opening project");

			final Project project = ProjectManagerEx.getInstanceEx().loadProject(projectDir.getPath());

			assert project != null;

			((ProjectManagerImpl) ProjectManagerEx.getInstance()).fireProjectOpened(project);

			((FileManagerImpl) ((PsiManagerEx) PsiManager.getInstance(project)).getFileManager()).markInitialized();

			Artifact dist = ArtifactManager.getInstance(project).findArtifact("dist");
			if(dist == null)
			{
				throw new ExecuteFailedException("'dist' artifact not found\n");
			}

			CompilerClientConnector.getInstance(project).setClientConnection(new CompilerClientInterface()
			{
				@Override
				public void addMessage(@NotNull CompilerMessageCategory compilerMessageCategory, String s, String s2, int i, int i2) throws RemoteException
				{
					switch(compilerMessageCategory)
					{
						case ERROR:
							executeLogger.info(compilerMessageCategory + ":" + s);
							break;
						case WARNING:
						case INFORMATION:
						case STATISTICS:
							executeLogger.info(compilerMessageCategory + ":" + s);
							break;
					}
				}

				@Override
				public void compilationFinished(boolean aborted, int errors, int warnings) throws RemoteException
				{
				}

				@NotNull
				@Override
				public String getProjectDir() throws RemoteException
				{
					return projectDir.getPath();
				}
			});

			executeLogger.info("Starting compiling artifact");
			CompileScope artifactsScope = ArtifactCompileScope.createArtifactsScope(project, Collections.singletonList(dist), true);

			final Ref<Pair<Integer, Boolean>> ref = Ref.create();

			final Semaphore semaphore = new Semaphore();
			semaphore.down();
			CompilerManager.getInstance(project).make(artifactsScope, new CompileStatusNotification()
			{
				@Override
				public void finished(boolean aborted, int errors, int i2, CompileContext compileContext)
				{
					ref.set(Pair.create(errors, aborted));
					semaphore.up();
				}
			});

			semaphore.waitFor();

			Pair<Integer, Boolean> pair = ref.get();
			if(pair == null)
			{
				throw new ExecuteFailedException("No compilation result");
			}

			boolean success = !pair.getSecond() && pair.getFirst() == 0;
			if(success)
			{
				// no need message
			}
			else if(pair.getSecond())
			{
				throw new ExecuteFailedException("Compilation aborted");
			}
			else
			{
				throw new ExecuteFailedException("Compilation failed with " + pair.getFirst() + " errors.");
			}
		}
		catch(IOException e)
		{
			throw new ExecuteFailedException(e);
		}
		catch(JDOMException e)
		{
			throw new ExecuteFailedException(e);
		}
		catch(InvalidDataException e)
		{
			throw new ExecuteFailedException(e);
		}
	}

	private static void setupSdk(String sdkTypeName, String name, String home)
	{
		SdkType sdkType = null;
		for(SdkType temp : SdkType.EP_NAME.getExtensions())
		{
			if(temp.getName().equals(sdkTypeName))
			{
				sdkType = temp;
				break;
			}
		}

		assert sdkType != null;
		SdkImpl sdk = new SdkImpl(name, sdkType, home, SystemProperties.getJavaVersion());
		sdk.setVersionString(sdkType.getVersionString(sdk));

		sdkType.setupSdkPaths(sdk);

		SdkTable.getInstance().addSdk(sdk);
	}
}
