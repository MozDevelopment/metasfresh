package org.adempiere.model.copyRecord.impl;

import java.util.List;

import org.adempiere.ad.dao.IQueryBL;
import org.adempiere.model.InterfaceWrapperHelper;
import org.adempiere.model.copyRecord.CopyRecordSupportChildInfo;
import org.adempiere.model.copyRecord.ICopyRecordSupportDAO;
import org.adempiere.util.Services;
import org.adempiere.util.proxy.Cached;
import org.compiere.model.I_AD_Column;
import org.compiere.model.I_AD_Table;

import com.google.common.collect.ImmutableList;

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

public class CopyRecordSupportDAO implements ICopyRecordSupportDAO
{
	@Override
	@Cached(cacheName = I_AD_Table.Table_Name + "#CopyRecordSupportChildInfo", expireMinutes = Cached.EXPIREMINUTES_Never)
	public List<CopyRecordSupportChildInfo> retrieveCopyRecordSupportChildInfos(final String parentTableName, final String parentKeyColumnName)
	{
		final String linkColumnName = parentKeyColumnName;

		return Services.get(IQueryBL.class)
				//
				// Get all parent link columns which match the given column name
				.createQueryBuilder(I_AD_Column.class)
				.addOnlyActiveRecordsFilter()
				.addEqualsFilter(I_AD_Column.COLUMN_ColumnName, linkColumnName)
				.addEqualsFilter(I_AD_Column.COLUMN_IsParent, true)
				//
				// Get their tables => those are the child tables for our given parentTableName
				.andCollect(I_AD_Column.COLUMN_AD_Table_ID)
				.addOnlyActiveRecordsFilter()
				.addEqualsFilter(I_AD_Table.COLUMN_IsView, false)
				//
				// Stream the child tables
				.create()
				.stream(I_AD_Table.class)
				//
				// Skip the child tables which are not copy-able
				.filter(childTable -> isCopyTable(childTable.getTableName()))
				//
				// Create the the CopyRecordSupportChildInfo
				.map(childTable -> CopyRecordSupportChildInfo.builder()
						.name(InterfaceWrapperHelper.getModelTranslationMap(childTable).getColumnTrl(I_AD_Table.COLUMNNAME_Name, childTable.getName()))
						.tableName(childTable.getTableName())
						.linkColumnName(linkColumnName)
						.parentTableName(parentTableName)
						.parentColumnName(parentKeyColumnName)
						.build())
				//
				.collect(ImmutableList.toImmutableList());
	}

	/**
	 * verify if a table can or not be copied
	 *
	 * @param tableName
	 * @return true if the table can be copied
	 */
	private static final boolean isCopyTable(final String tableName)
	{
		final String upperTableName = tableName.toUpperCase();
		final boolean isCopyTable = !upperTableName.endsWith("_ACCT") // acct table
				&& !upperTableName.startsWith("I_") // import tables
				&& !upperTableName.endsWith("_TRL") // translation tables
				&& !upperTableName.startsWith("M_COST") // cost tables
				&& !upperTableName.startsWith("T_") // temporary tables
				&& !upperTableName.equals("M_PRODUCT_COSTING") // product costing
				&& !upperTableName.equals("M_STORAGE") // storage table
				&& !upperTableName.equals("C_BP_WITHHOLDING") // at Patrick's request, this was removed, because is not used
				&& !(upperTableName.startsWith("M_") && upperTableName.endsWith("MA"));

		return isCopyTable;
	}

}
