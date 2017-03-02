package org.compiere.util;

import javax.annotation.concurrent.Immutable;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

/*
 * #%L
 * de.metas.adempiere.adempiere.base
 * %%
 * Copyright (C) 2017 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

/**
 * Cache invalidation request.
 * 
 * @author metas-dev <dev@metasfresh.com>
 *
 */
@Immutable
public final class CacheInvalidateRequest
{
	public static CacheInvalidateRequest table(final String tableName)
	{
		final int recordId = RECORD_ID_ALL;
		final String childTableName = null;
		final int childRecordId = -1;
		return new CacheInvalidateRequest(tableName, recordId, childTableName, childRecordId);
	}

	public static CacheInvalidateRequest record(final String tableName, final int recordId)
	{
		final String childTableName = null;
		final int childRecordId = -1;
		return new CacheInvalidateRequest(tableName, recordId, childTableName, childRecordId);
	}

	public static CacheInvalidateRequest childTable(final String tableName, final int recordId, final String childTableName)
	{
		final int childRecordId = RECORD_ID_ALL;
		return new CacheInvalidateRequest(tableName, recordId, childTableName, childRecordId);
	}

	public static CacheInvalidateRequest childRecord(final String tableName, final int recordId, final String childTableName, final int childRecordId)
	{
		return new CacheInvalidateRequest(tableName, recordId, childTableName, childRecordId);
	}

	private static final int RECORD_ID_ALL = -100;

	private transient Integer _hashcode;

	private final String tableName;
	private final int recordId;
	private final String childTableName;
	private final int childRecordId;

	public CacheInvalidateRequest(final String tableName, final int recordId, final String childTableName, final int childRecordId)
	{
		this.tableName = tableName;
		this.recordId = recordId;
		this.childTableName = childTableName;
		this.childRecordId = childRecordId;

	}

	@Override
	public String toString()
	{
		return MoreObjects.toStringHelper(this)
				.omitNullValues()
				.add("tableName", tableName)
				.add("recordId", recordId)
				.add("childTableName", childTableName)
				.add("childRecordId", childRecordId)
				.toString();
	}

	/**
	 * Returns <code>true</code> if both this and the given <code>obj</code> are TableRecordReferences and have the same <code>AD_Table_ID</code> and <code>Record_ID</code>.
	 */
	@Override
	public boolean equals(final Object obj)
	{
		if (this == obj)
		{
			return true;
		}
		else if (obj instanceof CacheInvalidateRequest)
		{
			final CacheInvalidateRequest other = (CacheInvalidateRequest)obj;
			return Objects.equal(tableName, other.tableName)
					&& recordId == other.recordId
					&& Objects.equal(childTableName, other.childTableName)
					&& childRecordId == other.childRecordId;
		}
		else
		{
			return false;
		}
	}

	@Override
	public int hashCode()
	{
		if (_hashcode == null)
		{
			_hashcode = Objects.hashCode(
					childTableName, childRecordId //
					, tableName, recordId //
			);
		}
		return _hashcode;
	}

	public String getTableName()
	{
		Preconditions.checkNotNull(tableName);
		return tableName;
	}

	public int getRecordId()
	{
		return recordId;
	}

	public String getChildTableName()
	{
		return childTableName;
	}

	public int getChildRecordId()
	{
		return childRecordId;
	}

	public boolean isCompleteReset()
	{
		return tableName == null;
	}

	public boolean isTableReset()
	{
		return tableName != null && recordId == RECORD_ID_ALL;
	}

	public boolean hasChild()
	{
		return childTableName != null;
	}

}
