package consulo.cold.runner.execute.target;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import com.google.gson.Gson;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTable;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.io.ZipUtil;
import consulo.cold.runner.execute.ExecuteFailedException;
import consulo.cold.runner.execute.ExecuteLogger;
import consulo.cold.runner.execute.ExecuteTarget;
import consulo.cold.runner.util.DownloadUtil;

/**
 * @author VISTALL
 * @since 11.04.2016
 */
public class PrepareDependenciesTarget implements ExecuteTarget
{
	protected static final PrepareDependenciesTarget ourInstance = new PrepareDependenciesTarget();

	private static final String ourLibraryPrefix = "consulo-plugin: ";
	private static final String ourDefaultPluginHost = "http://must-be.org/api/v2/consulo/plugins/";

	@Override
	public void execute(@NotNull ExecuteLogger executeLogger, @NotNull UserDataHolder executeContext) throws ExecuteFailedException
	{
		executeLogger.info("Preparing dependencies");

		File workDir = executeContext.getUserData(WORKING_DIRECTORY);

		File depDir = new File(workDir, "dep");

		if(!FileUtilRt.delete(depDir))
		{
			throw new ExecuteFailedException("Can't delete directory: " + depDir.getPath());
		}

		if(!FileUtilRt.createDirectory(depDir))
		{
			throw new ExecuteFailedException("Can't create directory: " + depDir.getPath());
		}

		String consuloVersion = null;
		Sdk sdk = SdkTable.getInstance().findSdk("Consulo SNAPSHOT");
		if(sdk != null)
		{
			consuloVersion = sdk.getVersionString();
		}

		if(consuloVersion == null)
		{
			throw new ExecuteFailedException("Failed to determinate version of Consulo SDK");
		}

		Set<String> originalDeps = new HashSet<>();

		File librariesDir = new File(workDir, ".consulo/libraries");
		if(librariesDir.exists())
		{
			for(File file : librariesDir.listFiles(x -> x.getName().endsWith(".xml")))
			{
				try
				{
					Element element = JDOMUtil.load(file);
					Element libraryElement = element.getChild("library");
					if(libraryElement == null)
					{
						continue;
					}
					String name = libraryElement.getAttributeValue("name");

					if(name != null && name.startsWith(ourLibraryPrefix))
					{
						String pluginId = name.substring(ourLibraryPrefix.length(), name.length());

						originalDeps.add(pluginId);
					}
				}
				catch(JDOMException | IOException e)
				{
					throw new ExecuteFailedException(e);
				}
			}
		}

		executeLogger.info("Downloading plugin list...");
		PluginJson[] plugins;
		try
		{
			InputStream inputStream = new URL(ourDefaultPluginHost + "list?channel=nightly&platformVersion=" + consuloVersion).openStream();

			plugins = new Gson().fromJson(new InputStreamReader(inputStream, StandardCharsets.UTF_8), PluginJson[].class);
		}
		catch(IOException e)
		{
			throw new ExecuteFailedException(e);
		}
		executeLogger.info("Downloaded plugin list [" + plugins.length + "]");

		Set<String> deepDependencies = new TreeSet<>();

		executeLogger.info("Collected original dependencies: " + originalDeps);

		for(String pluginId : originalDeps)
		{
			collectDependencies(pluginId, deepDependencies, plugins);
		}

		executeLogger.info("Collected deep dependencies: " + deepDependencies);

		try
		{
			executeLogger.info("Downloading dependencies...");
			for(String deepDependency : deepDependencies)
			{
				String downloadUrl = ourDefaultPluginHost + "download?channel=nightly&platformVersion=" + consuloVersion + "&pluginId=" + URLEncoder.encode(deepDependency, "UTF-8") + "&id=cold";

				File targetFileToDownload = FileUtil.createTempFile("download_target", ".zip");
				File tempTargetFileToDownload = FileUtil.createTempFile("temp_download_target", ".zip");

				executeLogger.info("Downloading plugin: " + deepDependency);
				DownloadUtil.downloadAtomically(executeLogger, downloadUrl, targetFileToDownload, tempTargetFileToDownload);

				executeLogger.info("Extracting plugin: " + deepDependency);
				ZipUtil.extract(targetFileToDownload, depDir, null);
			}
		}
		catch(Exception e)
		{
			throw new ExecuteFailedException(e);
		}
	}

	private void collectDependencies(String pluginId, Set<String> deps, PluginJson[] plugins)
	{
		if(!deps.add(pluginId))
		{
			return;
		}

		for(PluginJson plugin : plugins)
		{
			if(pluginId.equals(plugin.id))
			{
				for(String dependencyId : plugin.dependencies)
				{
					collectDependencies(dependencyId, deps, plugins);
				}
			}
		}
	}
}
