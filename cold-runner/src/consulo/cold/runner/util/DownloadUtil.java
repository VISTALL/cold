/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package consulo.cold.runner.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.containers.Predicate;
import consulo.cold.runner.execute.ExecuteLogger;

/**
 * @author Sergey Simonchik
 */
public class DownloadUtil
{
	/**
	 * Downloads content of {@code url} to {@code outputFile} atomically.<br/>
	 * {@code outputFile} isn't modified if an I/O error occurs or {@code contentChecker} is provided and returns false on the downloaded content.
	 * More formally, the steps are:
	 * <ol>
	 * <li>Download {@code url} to {@code tempFile}. Stop in case of any I/O errors.</li>
	 * <li>Stop if {@code contentChecker} is provided, and it returns false on the downloaded content.</li>
	 * <li>Move {@code tempFile} to {@code outputFile}. On most OS this operation is done atomically.</li>
	 * </ol>
	 * <p/>
	 * Motivation: some web filtering products return pure HTML with HTTP 200 OK status instead of
	 * the asked content.
	 *
	 * @param indicator      progress indicator
	 * @param url            url to download
	 * @param outputFile     output file
	 * @param tempFile       temporary file to download to. This file is deleted on method exit.
	 * @param contentChecker checks whether the downloaded content is OK or not
	 * @throws IOException if an I/O error occurs
	 * @returns true if no {@code contentChecker} is provided or the provided one returned true
	 */
	public static boolean downloadAtomically(@NotNull ExecuteLogger indicator,
			@NotNull String url,
			@NotNull File outputFile,
			@NotNull File tempFile,
			@Nullable Predicate<String> contentChecker) throws IOException
	{
		try
		{
			downloadContentToFile(indicator, url, tempFile);
			if(contentChecker != null)
			{
				String content = FileUtil.loadFile(tempFile);
				if(!contentChecker.apply(content))
				{
					return false;
				}
			}
			rename(tempFile, outputFile);
			return true;
		}
		finally
		{
			FileUtilRt.delete(tempFile);
		}
	}

	/**
	 * Downloads content of {@code url} to {@code outputFile} atomically.
	 * {@code outputFile} won't be modified in case of any I/O download errors.
	 *
	 * @param indicator  progress indicator
	 * @param url        url to download
	 * @param outputFile output file
	 * @param tempFile   temporary file to download to. This file is deleted on method exit.
	 */
	public static void downloadAtomically(@NotNull ExecuteLogger indicator, @NotNull String url, @NotNull File outputFile, @NotNull File tempFile) throws IOException
	{
		downloadAtomically(indicator, url, outputFile, tempFile, null);
	}

	public static void downloadContentToFile(@NotNull ExecuteLogger progress, @NotNull String url, @NotNull File outputFile) throws IOException
	{
		boolean parentDirExists = FileUtil.createParentDirs(outputFile);
		if(!parentDirExists)
		{
			throw new IOException("Parent dir of '" + outputFile.getAbsolutePath() + "' can not be created!");
		}
		OutputStream out = new FileOutputStream(outputFile);
		try
		{
			download(progress, url, out);
		}
		finally
		{
			out.close();
		}
	}

	private static void download(@NotNull ExecuteLogger indicator, @NotNull String location, @NotNull OutputStream output) throws IOException
	{
		HttpURLConnection urlConnection = (HttpURLConnection) new URL(location).openConnection();
		try
		{
			int timeout = (int) TimeUnit.MINUTES.toMillis(2);
			urlConnection.setConnectTimeout(timeout);
			urlConnection.setReadTimeout(timeout);
			urlConnection.connect();
			InputStream in = urlConnection.getInputStream();

			copyStreamContent(in, output);
		}
		catch(IOException e)
		{
			throw new IOException("Can not download '" + location + "', response code: " + urlConnection.getResponseCode() + ", response message: " + urlConnection.getResponseMessage() + ", " +
					"headers: " + urlConnection.getHeaderFields(), e);
		}
		finally
		{
			try
			{
				urlConnection.disconnect();
			}
			catch(Exception e)
			{
				indicator.warn("Exception at disconnect()", e);
			}
		}
	}

	public static void rename(@NotNull File source, @NotNull File target) throws IOException
	{
		if(source.renameTo(target))
		{
			return;
		}
		if(!source.exists())
		{
			return;
		}

		FileUtilRt.copy(source, target);
		FileUtilRt.delete(source);
	}

	public static int copyStreamContent(@NotNull InputStream inputStream, @NotNull OutputStream outputStream) throws IOException, ProcessCanceledException
	{
		final byte[] buffer = new byte[8 * 1024];
		int count;
		int total = 0;
		while((count = inputStream.read(buffer)) > 0)
		{
			outputStream.write(buffer, 0, count);
			total += count;
		}
		return total;
	}
}
