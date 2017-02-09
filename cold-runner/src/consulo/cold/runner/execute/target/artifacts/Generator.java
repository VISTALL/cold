/*
 * Copyright 2013-2016 must-be.org
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

package consulo.cold.runner.execute.target.artifacts;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.jetbrains.annotations.Nullable;
import com.Ostermiller.util.BinaryDataException;
import com.Ostermiller.util.LineEnds;
import consulo.cold.runner.execute.ExecuteLogger;

/**
 * @author VISTALL
 * @since 04-Sep-16
 */
public class Generator
{
	private static final String[] ourExecutable = new String[]{
			// linux
			"Consulo/consulo.sh",
			"Consulo/platform/buildSNAPSHOT/bin/launcher.sh",
			"Consulo/platform/buildSNAPSHOT/bin/fsnotifier",
			"Consulo/platform/buildSNAPSHOT/bin/fsnotifier64",
			// mac
			"Consulo.app/Contents/platform/buildSNAPSHOT/bin/fsnotifier",
			"Consulo.app/Contents/platform/buildSNAPSHOT/bin/restarter",
			"Consulo.app/Contents/platform/buildSNAPSHOT/bin/printenv.py",
			"Consulo.app/Contents/MacOS/consulo",
	};

	private static final String[] ourMacSkipJdkList = new String[]{
			"jdk/Contents/Home/demo/",
			"jdk/Contents/Home/include/",
			"jdk/Contents/Home/lib/",
			"jdk/Contents/Home/man/",
			"jdk/Contents/Home/sample/",
			"jdk/Contents/Home/src.zip",
	};

	private static final String ourBuildSNAPSHOT = "buildSNAPSHOT";

	private File myDistPath;
	private File myTargetDir;
	private int myBuildNumber;
	private ExecuteLogger myListener;

	public Generator(File distPath, File targetDir, int buildNumber, ExecuteLogger listener)
	{
		myDistPath = distPath;
		myTargetDir = targetDir;
		myBuildNumber = buildNumber;
		myListener = listener;
	}

	public void buildDistributionInArchive(String distZip, @Nullable String jdkArchivePath, String path, String archiveOutType) throws Exception
	{
		myListener.info("Build: " + path);

		ArchiveStreamFactory factory = new ArchiveStreamFactory();

		final File fileZip = new File(myDistPath, distZip);

		final List<String> executables = Arrays.asList(ourExecutable);

		try (OutputStream pathStream = createOutputStream(archiveOutType, path))
		{
			ArchiveOutputStream archiveOutputStream = factory.createArchiveOutputStream(archiveOutType, pathStream);
			if(archiveOutputStream instanceof TarArchiveOutputStream)
			{
				((TarArchiveOutputStream) archiveOutputStream).setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
			}

			// move Consulo to archive, and change permissions
			try (InputStream is = new FileInputStream(fileZip))
			{
				try (ArchiveInputStream ais = factory.createArchiveInputStream(ArchiveStreamFactory.ZIP, is))
				{
					ArchiveEntry tempEntry = ais.getNextEntry();
					while(tempEntry != null)
					{
						final ArchiveEntryWrapper newEntry = createEntry(archiveOutType, tempEntry.getName(), tempEntry);

						newEntry.setMode(extractMode(tempEntry));
						newEntry.setTime(tempEntry.getLastModifiedDate().getTime());

						if(executables.contains(tempEntry.getName()))
						{
							newEntry.setMode(0b111_101_101);
						}

						copyEntry(archiveOutputStream, ais, tempEntry, newEntry);

						tempEntry = ais.getNextEntry();
					}
				}
			}

			boolean mac = distZip.contains("mac");

			// jdk check
			if(jdkArchivePath != null)
			{
				try (InputStream is = new FileInputStream(jdkArchivePath))
				{
					try (GzipCompressorInputStream gz = new GzipCompressorInputStream(is))
					{
						try (ArchiveInputStream ais = factory.createArchiveInputStream(ArchiveStreamFactory.TAR, gz))
						{
							ArchiveEntry tempEntry = ais.getNextEntry();
							while(tempEntry != null)
							{
								final String name = tempEntry.getName();

								// is our path
								if(!mac && name.startsWith("jre/"))
								{
									final ArchiveEntryWrapper jdkEntry = createEntry(archiveOutType, "Consulo/platform/buildSNAPSHOT/" + name, tempEntry);
									jdkEntry.setMode(extractMode(tempEntry));
									jdkEntry.setTime(tempEntry.getLastModifiedDate().getTime());

									copyEntry(archiveOutputStream, ais, tempEntry, jdkEntry);
								}
								else if(mac && name.startsWith("jdk"))
								{
									boolean needAddToArchive = true;
									for(String prefix : ourMacSkipJdkList)
									{
										if(name.startsWith(prefix))
										{
											needAddToArchive = false;
										}
									}

									if(needAddToArchive)
									{
										final ArchiveEntryWrapper jdkEntry = createEntry(archiveOutType, "Consulo.app/Contents/platform/buildSNAPSHOT/jre/" + name, tempEntry);
										jdkEntry.setMode(extractMode(tempEntry));
										jdkEntry.setTime(tempEntry.getLastModifiedDate().getTime());

										copyEntry(archiveOutputStream, ais, tempEntry, jdkEntry);
									}
								}

								tempEntry = ais.getNextEntry();
							}
						}
					}
				}
			}

			archiveOutputStream.finish();
		}
	}

	private static int extractMode(ArchiveEntry entry)
	{
		if(entry instanceof TarArchiveEntry)
		{
			return ((TarArchiveEntry) entry).getMode();
		}
		return entry.isDirectory() ? TarArchiveEntry.DEFAULT_DIR_MODE : TarArchiveEntry.DEFAULT_FILE_MODE;
	}

	private static void copyEntry(ArchiveOutputStream archiveOutputStream, ArchiveInputStream ais, ArchiveEntry tempEntry, ArchiveEntryWrapper newEntry) throws IOException
	{
		byte[] data = null;
		if(!tempEntry.isDirectory())
		{
			try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream())
			{
				IOUtils.copy(ais, byteStream);
				data = byteStream.toByteArray();
			}

			// change line breaks
			try (ByteArrayOutputStream stream = new ByteArrayOutputStream())
			{
				if(LineEnds.convert(new ByteArrayInputStream(data), stream, LineEnds.STYLE_UNIX))
				{
					data = stream.toByteArray();
				}
			}
			catch(BinaryDataException ignored)
			{
				// ignore binary data
			}
		}

		if(data != null)
		{
			newEntry.setSize(data.length);
		}

		archiveOutputStream.putArchiveEntry(newEntry.getArchiveEntry());

		if(data != null)
		{
			IOUtils.copy(new ByteArrayInputStream(data), archiveOutputStream);
		}

		archiveOutputStream.closeArchiveEntry();
	}

	private ArchiveEntryWrapper createEntry(String type, String name, ArchiveEntry tempEntry)
	{
		name = replaceBuildDirectory(name);

		if(type.equals(ArchiveStreamFactory.TAR))
		{
			return new ArchiveEntryWrapper.Tar(name, tempEntry);
		}
		return new ArchiveEntryWrapper.Zip(name);
	}

	private String replaceBuildDirectory(String entryName)
	{
		if(entryName.contains(ourBuildSNAPSHOT))
		{
			return entryName.replace(ourBuildSNAPSHOT, "build" + myBuildNumber);
		}
		return entryName;
	}

	private OutputStream createOutputStream(String type, String prefix) throws Exception
	{
		final String fileName;
		if(type.equals(ArchiveStreamFactory.ZIP))
		{
			fileName = prefix + ".zip";
		}
		else if(type.equals(ArchiveStreamFactory.TAR))
		{
			fileName = prefix + ".tar.gz";
		}
		else
		{
			throw new IllegalArgumentException(type);
		}

		File child = new File(myTargetDir, fileName);

		final OutputStream outputStream = new FileOutputStream(child);
		if(type.equals(ArchiveStreamFactory.TAR))
		{
			return new GzipCompressorOutputStream(outputStream);
		}
		return outputStream;
	}
}