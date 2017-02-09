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

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;

/**
 * @author VISTALL
 * @since 17-Jul-16
 */
public abstract class ArchiveEntryWrapper<E extends ArchiveEntry>
{
	public static class Tar extends ArchiveEntryWrapper<TarArchiveEntry>
	{
		public Tar(String name, ArchiveEntry tempEntry)
		{
			super(create(name, tempEntry));
		}

		private static TarArchiveEntry create(String name, ArchiveEntry tempEntry)
		{
			if(tempEntry instanceof TarArchiveEntry)
			{
				byte flags = 0;
				if(tempEntry.isDirectory())
				{
					flags = TarConstants.LF_DIR;
				}
				else if(((TarArchiveEntry) tempEntry).isSymbolicLink())
				{
					flags = TarConstants.LF_SYMLINK;
				}
				else if(((TarArchiveEntry) tempEntry).isLink())
				{
					flags = TarConstants.LF_LINK;
				}
				else
				{
					flags = TarConstants.LF_NORMAL;
				}

				String link = ((TarArchiveEntry) tempEntry).getLinkName();
				final TarArchiveEntry entry = new TarArchiveEntry(name, flags);
				if(link != null)
				{
					entry.setLinkName(link);
				}
				return entry;
			}
			return new TarArchiveEntry(name);
		}

		@Override
		public void setTime(long date)
		{
			myArchiveEntry.setModTime(date);
		}

		@Override
		public void setMode(int mode)
		{
			myArchiveEntry.setMode(mode);
		}

		@Override
		public void setSize(long size)
		{
			myArchiveEntry.setSize(size);
		}
	}

	public static class Zip extends ArchiveEntryWrapper<ZipArchiveEntry>
	{
		public Zip(String name)
		{
			super(new ZipArchiveEntry(name));
		}

		@Override
		public void setTime(long date)
		{
			myArchiveEntry.setTime(date);
		}

		@Override
		public void setMode(int mode)
		{
		}

		@Override
		public void setSize(long size)
		{
			myArchiveEntry.setSize(size);
		}
	}

	protected final E myArchiveEntry;

	protected ArchiveEntryWrapper(E archiveEntry)
	{
		myArchiveEntry = archiveEntry;
	}

	public E getArchiveEntry()
	{
		return myArchiveEntry;
	}

	public boolean isDirectory()
	{
		return myArchiveEntry.isDirectory();
	}

	public abstract void setTime(long date);

	public abstract void setMode(int mode);

	public abstract void setSize(long size);
}