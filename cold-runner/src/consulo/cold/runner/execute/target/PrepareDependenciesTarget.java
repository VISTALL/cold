package consulo.cold.runner.execute.target;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.MultiMap;
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
	private static final String ourDefaultPluginHost = "http://must-be.org/consulo/plugins/%s";

	@Override
	public void execute(@NotNull ExecuteLogger executeLogger, @NotNull UserDataHolder executeContext) throws ExecuteFailedException
	{
		executeLogger.info("Preparing dependencies");

		File depDir = new File(executeContext.getUserData(WORKING_DIRECTORY), "dep");

		if(!FileUtilRt.delete(depDir))
		{
			throw new ExecuteFailedException("Can't delete directory: " + depDir.getPath());
		}

		if(!FileUtilRt.createDirectory(depDir))
		{
			throw new ExecuteFailedException("Can't create directory: " + depDir.getPath());
		}
		try
		{
			URLConnection connection = new URL("http://must-be.org/vulcan/projects.jsp").openConnection();
			connection.connect();

			InputStream inputStream = null;
			try
			{
				Document document = JDOMUtil.loadDocument(inputStream = connection.getInputStream());

				MultiMap<String, String> map = new MultiMap<String, String>();
				for(Element element : document.getRootElement().getChildren())
				{
					String projectName = element.getChildText("name");
					Element dependencies = element.getChild("dependencies");
					if(dependencies != null)
					{
						for(Element dependencyElement : dependencies.getChildren())
						{
							String textTrim = dependencyElement.getTextTrim();
							if(Comparing.equal(textTrim, "consulo"))
							{
								continue;
							}
							map.putValue(projectName, textTrim);
						}
					}
				}

				inputStream.close();

				String projectName = executeContext.getUserData(PROJECT_NAME);
				if(!map.containsKey(projectName))
				{
					return;
				}
				executeLogger.info("Downloading plugin list...");
				connection = new URL(String.format(ourDefaultPluginHost, "list")).openConnection();
				connection.connect();

				MultiMap<String, String> buildProjectToId = new MultiMap<String, String>();
				document = JDOMUtil.loadDocument(inputStream = connection.getInputStream());

				for(Element categoryElement : document.getRootElement().getChildren())
				{
					for(Element ideaPluginElement : categoryElement.getChildren())
					{
						String idText = ideaPluginElement.getChildText("id");
						String buildProjectIdText = ideaPluginElement.getChildText("build-project");
						if(StringUtil.isEmpty(idText) || StringUtil.isEmpty(buildProjectIdText))
						{
							continue;
						}
						buildProjectToId.putValue(buildProjectIdText, idText);
					}
				}

				Set<String> toDownloadIds = new HashSet<String>();
				collectDependencies(projectName, map, toDownloadIds, buildProjectToId);

				String uuid = "cold";

				for(String toDownloadId : toDownloadIds)
				{
					String url = String.format(ourDefaultPluginHost, "download?id=") + URLEncoder.encode(toDownloadId, "UTF8") +
							"&build=SNAPSHOT&uuid=" + URLEncoder.encode(uuid, "UTF8");

					File targetFileToDownload = FileUtil.createTempFile("download_target", ".zip");
					File tempTargetFileToDownload = FileUtil.createTempFile("temp_download_target", ".zip");

					executeLogger.info("Downloading plugin: " + toDownloadId);
					DownloadUtil.downloadAtomically(executeLogger, url, targetFileToDownload, tempTargetFileToDownload);

					executeLogger.info("Extracting plugin: " + toDownloadId);
					ZipUtil.extract(targetFileToDownload, depDir, null);
				}
			}
			catch(JDOMException e)
			{
				executeLogger.warn(e);
			}
			finally
			{
				StreamUtil.closeStream(inputStream);
			}
		}
		catch(Exception e)
		{
			throw new ExecuteFailedException(e);
		}
	}

	private static void collectDependencies(String projectName, MultiMap<String, String> dependenciesInBuild, Collection<String> make, MultiMap<String, String> buildProjectToId)
	{
		Collection<String> dependencies = dependenciesInBuild.get(projectName);
		for(String dependency : dependencies)
		{
			for(String id : buildProjectToId.get(dependency))
			{
				make.add(id);
			}

			collectDependencies(dependency, dependenciesInBuild, make, buildProjectToId);
		}
	}
}
