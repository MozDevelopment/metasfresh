package org.adempiere.model.copyRecord;

/*
 * #%L
 * de.metas.adempiere.adempiere.base
 * %%
 * Copyright (C) 2015 metas GmbH
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

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.StringTokenizer;

import javax.annotation.OverridingMethodsMustInvokeSuper;

import org.adempiere.ad.persistence.IModelInternalAccessor;
import org.adempiere.ad.persistence.TableModelLoader;
import org.adempiere.ad.security.TableAccessLevel;
import org.adempiere.ad.trx.api.ITrx;
import org.adempiere.exceptions.DBException;
import org.adempiere.model.InterfaceWrapperHelper;
import org.adempiere.util.Services;
import org.adempiere.util.api.IMsgBL;
import org.compiere.model.GridField;
import org.compiere.model.MUser;
import org.compiere.model.PO;
import org.compiere.model.POInfo;
import org.compiere.model.Query;
import org.compiere.util.DB;
import org.compiere.util.DisplayType;
import org.compiere.util.Env;
import org.compiere.util.Evaluatee;
import org.compiere.util.Evaluatees;
import org.compiere.util.Evaluator;
import org.slf4j.Logger;

import com.google.common.collect.ImmutableList;

import de.metas.logging.LogManager;

/**
 * @author Cristina Ghita, METAS.RO
 *
 */
public class GeneralCopyRecordSupport implements CopyRecordSupport
{
	private static final String COLUMNNAME_Value = "Value";
	private static final String COLUMNNAME_Name = "Name";
	private static final String COLUMNNAME_IsActive = "IsActive";

	private String _parentKeyColumnName = null;
	private List<CopyRecordSupportChildInfo> childrenInfo;

	private PO _parentPO = null; // needed for getValueToCopy
	private int AD_Window_ID = -1; // needed for getValueToCopy (when getting default value from user preferences)

	private static final transient Logger log = LogManager.getLogger(GeneralCopyRecordSupport.class);

	@Override
	public final Optional<PO> copyRoot(final PO oldPO, final String trxName)
	{
		final int newParentId = -1; // no parent, this is the root
		final Optional<PO> newPO = copyRecord(oldPO, newParentId, trxName);

		if (newPO.isPresent())
		{
			copyChildren(newPO.get(), trxName);
		}

		return newPO;
	}

	@Override
	public final void copyChildren(final PO newParentPO, final String trxName)
	{
		final int oldParentId = CopyRecordFactory.getOldPOId(newParentPO);

		for (final CopyRecordSupportChildInfo childTableInfo : getSuggestedChildren(newParentPO))
		{
			final CopyRecordFactory childCopierFactory = CopyRecordFactory.builder()
					.tableName(childTableInfo.getTableName())
					.adWindowId(getAD_Window_ID())
					//
					.parentPO(newParentPO)
					.parentKeyColumn(childTableInfo.getLinkColumnName())
					//
					.build();

			for (final Iterator<? extends PO> it = retrievePOsForParent(childTableInfo.getTableName(), childTableInfo.getLinkColumnName(), oldParentId); it.hasNext();)
			{
				final PO oldChildPO = it.next();
				final CopyRecordSupport childCRS = childCopierFactory.create();
				final Optional<PO> newChildPO = childCRS.copyChild(oldChildPO, newParentPO.get_ID(), trxName);
				log.info("Copied: {} -> {}", oldChildPO, newChildPO);
			}
		}
	}

	@Override
	public final Optional<PO> copyChild(final PO oldChildPO, final int newParentId, final String trxName)
	{
		return copyRecord(oldChildPO, newParentId, trxName);
	}

	private final Optional<PO> copyRecord(final PO oldChildPO, final int newParentId, final String trxName)
	{
		// Check if we shall skip this record
		if (!isCopyRecord(oldChildPO))
		{
			return Optional.empty();
		}

		final PO newChildPO = TableModelLoader.instance.newPO(Env.getCtx(), oldChildPO.get_TableName(), trxName);

		//
		// Copy all values from "fromPO" to "newPO"
		InterfaceWrapperHelper.copy()
				.setCalculatedValueToCopyExtractor((columnName, to, from) -> null)// TODO
				.copy(newChildPO, oldChildPO);
		// // FIXME: the only reason why we are setting "this" to newChildPO is because we want "getValueToCopy" to be called. Get rid of it.
		// newChildPO.setDynAttribute(CopyRecordFactory.DYNATTR_CopyRecordSupport, this); // need this for getting defaultValues at copy in PO
		// PO.copyValues(oldChildPO, newChildPO, true);
		// // reset for avoiding copy same object twice
		// newChildPO.setDynAttribute(CopyRecordFactory.DYNATTR_CopyRecordSupport, null);

		// Update parent link column
		if (newParentId > 0)
		{
			final String parentKeyColumn = getParentKeyColumn();
			newChildPO.set_Value(parentKeyColumn, newParentId);
		}

		//
		// Refresh PK columns
		// TODO: in the past this was needed to make sure the PK (after copy) was propagated to all other variables. Not sure if this is still needed...
		for (final String columnName : newChildPO.get_KeyColumns())
		{
			newChildPO.set_Value(columnName, newChildPO.get_Value(columnName));
		}

		// needs to set IsActive because is not copied
		if (newChildPO.get_ColumnIndex(COLUMNNAME_IsActive) >= 0)
		{
			newChildPO.set_Value(COLUMNNAME_IsActive, oldChildPO.get_Value(COLUMNNAME_IsActive));
		}
		//
		updateSpecialColumnNames(newChildPO);
		onRecordCopied(newChildPO, oldChildPO);
		//
		CopyRecordFactory.setOldPOId(newChildPO, oldChildPO.get_ID());
		//
		newChildPO.saveEx(trxName);

		return Optional.of(newChildPO);
	}

	protected boolean isCopyRecord(final PO po)
	{
		return true;
	}

	/**
	 * Called after the record was copied, right before saving it.
	 *
	 * @param to the copy
	 * @param from the source
	 */
	protected final void onRecordCopied(final PO to, final PO from)
	{
		for (final IOnRecordCopiedListener listener : onRecordCopiedListeners)
		{
			listener.onRecordCopied(to, from);
		}
	}

	@Override
	@OverridingMethodsMustInvokeSuper
	public void updateSpecialColumnNames(final PO to)
	{
		final int idxName = to.get_ColumnIndex(COLUMNNAME_Name);
		if (idxName >= 0)
		{
			final POInfo poInfo = to.getPOInfo();
			if (DisplayType.isText(poInfo.getColumnDisplayType(idxName)))
			{
				makeUnique(to, COLUMNNAME_Name);
			}
		}
		if (to.get_ColumnIndex(COLUMNNAME_Value) >= 0)
		{
			makeUnique(to, COLUMNNAME_Value);
		}
	}

	private static void makeUnique(final PO to, final String column)
	{
		final IMsgBL msgBL = Services.get(IMsgBL.class);

		final Format formatter = new SimpleDateFormat("yyyyMMdd:HH:mm:ss");

		final Properties ctx = Env.getCtx();
		final Timestamp ts = new Timestamp(System.currentTimeMillis());
		final String s = formatter.format(ts);
		final String name = MUser.getNameOfUser(Env.getAD_User_ID(ctx));

		final String language = Env.getAD_Language(ctx);
		final String msg = "(" + msgBL.getMsg(language, "CopiedOn", new String[] { s }) + " " + name + ")";

		final String oldValue = (String)to.get_Value(column);
		to.set_Value(column, oldValue + msg);
	}

	private static Iterator<? extends PO> retrievePOsForParent(final String childTableName, final String childLinkColumnName, final int parentId)
	{
		final String whereClause = childLinkColumnName + " = ? ";
		return new Query(Env.getCtx(), childTableName, whereClause, null)
				.setParameters(new Object[] { parentId })
				.iterate(null, false); // guaranteed = false because we are just fetching without changing
	}

	@Override
	@OverridingMethodsMustInvokeSuper
	public List<CopyRecordSupportChildInfo> getSuggestedChildren(final PO po)
	{
		//
		// Check user suggested list (if any)
		if (childrenInfo != null && !childrenInfo.isEmpty())
		{
			return childrenInfo;
		}

		return extractChildrenInfoFromParentPOInfo(po.getPOInfo());
	}

	public static List<CopyRecordSupportChildInfo> extractChildrenInfoFromParentPOInfo(final POInfo poInfo)
	{
		final String tableName = poInfo.getTableName();
		final String keyColumnName = poInfo.getKeyColumnName();
		if (keyColumnName == null)
		{
			// if we no primary key or composed primary key return empty list
			return ImmutableList.of();
		}

		return Services.get(ICopyRecordSupportDAO.class).retrieveCopyRecordSupportChildInfos(tableName, keyColumnName);
	}

	@Override
	public void setChildrenInfo(final List<CopyRecordSupportChildInfo> childrenInfo)
	{
		this.childrenInfo = childrenInfo;
	}

	@Override
	public final PO getParentPO()
	{
		return _parentPO;
	}

	@Override
	public final void setParentPO(final PO parentPO)
	{
		_parentPO = parentPO;
	}

	@Override
	public final String getParentKeyColumn()
	{
		return _parentKeyColumnName;
	}

	@Override
	public final void setParentKeyColumn(final String parentKeyColumn)
	{
		_parentKeyColumnName = parentKeyColumn;
	}

	/**
	 * metas: same method in GridField. TODO: refactoring
	 *
	 * @param value
	 * @param po
	 * @param columnName
	 * @return
	 */
	private static Object createDefault(final String value, final String columnName, final int displayType)
	{
		// true NULL
		if (value == null || value.isEmpty())
		{
			return null;
		}

		try
		{
			// IDs & Integer & CreatedBy/UpdatedBy
			if ("CreatedBy".equals(columnName)
					|| "UpdatedBy".equals(columnName)
					|| (columnName.endsWith("_ID") && DisplayType.isID(displayType))) // teo_sarca [ 1672725 ] Process parameter that ends with _ID but is boolean
			{
				try
				// defaults -1 => null
				{
					final int ii = Integer.parseInt(value);
					if (ii < 0)
					{
						return null;
					}
					return ii;
				}
				catch (final Exception e)
				{
					log.warn("Cannot parse ID '{}'. Returning ZERO.", value, e);
					return 0;
				}
			}
			// Integer
			if (DisplayType.Integer == displayType)
			{
				return Integer.parseInt(value);
			}

			// Number
			if (DisplayType.isNumeric(displayType))
			{
				return new BigDecimal(value);
			}

			// Timestamps
			if (DisplayType.isDate(displayType))
			{
				// try timestamp format - then date format -- [ 1950305 ]
				java.util.Date date = null;
				try
				{
					date = DisplayType.getTimestampFormat_Default().parse(value);
				}
				catch (final java.text.ParseException e)
				{
					date = DisplayType.getDateFormat_JDBC().parse(value);
				}
				return new Timestamp(date.getTime());
			}

			// Boolean
			if (DisplayType.YesNo == displayType)
			{
				return DisplayType.toBoolean(value);
			}

			// Default
			return value;
		}
		catch (final Exception ex)
		{
			log.error("Failed creating default value for {}. Considering it null.", columnName, ex);
			return null;
		}
	}

	/**
	 * Similar method to {@link org.compiere.model.GridField#getDefault()}, with one difference: the <code>AccessLevel</code> is only applied if the column has <code>IsCalculated='N'</code>.
	 *
	 * <pre>
	 * 	(a) Key/Parent/IsActive/SystemAccess
	 *      (b) SQL Default
	 * 	(c) Column Default		//	system integrity
	 *      (d) User Preference
	 * 	(e) System Preference
	 * 	(f) DataType Defaults
	 *
	 *  Don't default from Context => use explicit defaultValue
	 *  (would otherwise copy previous record)
	 * </pre>
	 *
	 * @return default value or null
	 */
	private static Object getDefault(final PO po, final String columnName, final PO parentPO, final int AD_Window_ID)
	{
		// TODO: until refactoring, keep in sync with org.compiere.model.GridField.getDefaultNoCheck()
		// Object defaultValue = null;

		/**
		 * (a) Key/Parent/IsActive/SystemAccess
		 */

		final IModelInternalAccessor modelAccessor = InterfaceWrapperHelper.getModelInternalAccessor(po);
		final String defaultLogic = modelAccessor.getDefaultValueLogic(columnName);
		final int displayType = modelAccessor.getDisplayType(columnName);

		if (defaultLogic == null)
		{
			return null;
		}

		if (modelAccessor.isKeyColumnName(columnName) || DisplayType.RowID == displayType || DisplayType.isLOB(displayType))
		{
			return null;
		}
		// Always Active
		if (columnName.equals("IsActive"))
		{
			log.debug("[IsActive] {}=Y", columnName);
			return DisplayType.toBooleanString(Boolean.TRUE);
		}

		// TODO: NOTE!! This is out of sync with org.compiere.model.GridField.getDefaultNoCheck()
		//
		// 07896: If PO column is considered calculated for AD_Org and AD_Client, consider using the System
		// Otherwise, treat them like any other column
		//
		if (modelAccessor.isCalculated(columnName))
		{
			// Set Client & Org to System, if System access
			final TableAccessLevel accessLevel = modelAccessor.getAccessLevel();
			if (accessLevel.isSystemOnly()
					&& (columnName.equals("AD_Client_ID") || columnName.equals("AD_Org_ID")))
			{
				log.debug("[SystemAccess] {}=0", columnName);
				return 0;
			}
			// Set Org to System, if Client access
			else if (accessLevel == TableAccessLevel.SystemPlusClient
					&& columnName.equals("AD_Org_ID"))
			{
				log.debug("[ClientAccess] {}=0", columnName);
				return 0;
			}
		}

		/**
		 * (b) SQL Statement (for data integity & consistency)
		 */
		String defStr = "";
		if (defaultLogic.startsWith("@SQL="))
		{
			String sql = defaultLogic.substring(5); // w/o tag

			final Evaluatee evaluatee = Evaluatees.composeNotNulls(po, parentPO);
			sql = Evaluator.parseContext(evaluatee, sql);
			if (sql.equals(""))
			{
				log.warn("(" + columnName + ") - Default SQL variable parse failed: " + defaultLogic);
			}
			else
			{
				PreparedStatement pstmt = null;
				ResultSet rs = null;
				try
				{
					pstmt = DB.prepareStatement(sql, ITrx.TRXNAME_ThreadInherited);
					rs = pstmt.executeQuery();
					if (rs.next())
					{
						defStr = rs.getString(1);
					}
					else
					{
						log.warn("(" + columnName + ") - no Result: " + sql);
					}
				}
				catch (final SQLException sqlEx)
				{
					log.warn("Failed fetching default SQL value for {}", columnName, new DBException(sqlEx, sql));
				}
				finally
				{
					DB.close(rs, pstmt);
					rs = null;
					pstmt = null;
				}
			}
			if (defStr == null && parentPO != null && parentPO.get_ColumnIndex(columnName) >= 0)
			{
				return parentPO.get_Value(columnName);
			}
			if (defStr != null && defStr.length() > 0)
			{
				log.debug("[SQL] {}={}", columnName, defStr);
				return createDefault(defStr, columnName, displayType);
			}
		} // SQL Statement

		/**
		 * (c) Field DefaultValue === similar code in AStartRPDialog.getDefault ===
		 */
		if (!defaultLogic.equals("") && !defaultLogic.startsWith("@SQL="))
		{
			defStr = ""; // problem is with texts like 'sss;sss'
			// It is one or more variables/constants
			final StringTokenizer st = new StringTokenizer(defaultLogic, ",;", false);
			while (st.hasMoreTokens())
			{
				defStr = st.nextToken().trim();
				if (defStr.equals("@SysDate@"))
				{
					return new Timestamp(System.currentTimeMillis());
				}
				else if (defStr.indexOf('@') != -1) // it is a variable
				{
					final Evaluatee evaluatee = Evaluatees.composeNotNulls(po, parentPO);
					defStr = Evaluator.parseContext(evaluatee, defStr.trim());
				}
				else if (defStr.indexOf("'") != -1)
				{
					defStr = defStr.replace('\'', ' ').trim();
				}

				if (!defStr.equals(""))
				{
					log.debug("[DefaultValue] {}={}", columnName, defStr);
					return createDefault(defStr, columnName, displayType);
				}
			} // while more Tokens
		} // Default value

		/**
		 * (d) Preference (user) - P|
		 */
		defStr = Env.getPreference(Env.getCtx(), AD_Window_ID, columnName, false);
		if (!defStr.equals(""))
		{
			log.debug("[UserPreference] {}={}", columnName, defStr);
			return createDefault(defStr, columnName, displayType);
		}

		/**
		 * (e) Preference (System) - # $
		 */
		defStr = Env.getPreference(Env.getCtx(), AD_Window_ID, columnName, true);
		if (!defStr.equals(""))
		{
			log.debug("[SystemPreference] {}={}", columnName, defStr);
			return createDefault(defStr, columnName, displayType);
		}

		/**
		 * (f) DataType defaults
		 */

		// Button to N
		if (DisplayType.Button == displayType && !columnName.endsWith("_ID"))
		{
			log.debug("[Button=N] {}", columnName);
			return DisplayType.toBooleanString(Boolean.FALSE);
		}
		// CheckBoxes default to No
		if (displayType == DisplayType.YesNo)
		{
			log.debug("[YesNo=N] {}", columnName);
			return DisplayType.toBooleanString(Boolean.FALSE);
		}
		// IDs remain null
		if (columnName.endsWith("_ID"))
		{
			log.debug("[ID=null] {}", columnName);
			return null;
		}
		// actual Numbers default to zero
		if (DisplayType.isNumeric(displayType))
		{
			log.debug("[Number=0] {}", columnName);
			return createDefault("0", columnName, displayType);
		}

		if (parentPO != null)
		{
			return parentPO.get_Value(columnName);
		}
		return null;
	}

	@Override
	public Object getValueToCopy(final PO to, final PO from, final String columnName)
	{
		return getDefault(to, columnName, getParentPO(), getAD_Window_ID());
	}

	@Override
	public Object getValueToCopy(final GridField gridField)
	{
		return gridField.getDefault();
	}

	@Override
	public final int getAD_Window_ID()
	{
		return AD_Window_ID;
	}

	@Override
	public final void setAD_Window_ID(final int aDWindowID)
	{
		AD_Window_ID = aDWindowID;
	}

	private final List<IOnRecordCopiedListener> onRecordCopiedListeners = new ArrayList<>();

	/**
	 * Allows other modules to install customer code to be executed each time a record was copied.
	 *
	 * @param listener
	 */
	@Override
	public final void addOnRecordCopiedListener(final IOnRecordCopiedListener listener)
	{
		onRecordCopiedListeners.add(listener);
	}
}
