package org.adempiere.model;

import java.util.List;

import org.adempiere.model.copyRecord.GeneralCopyRecordSupport;
import org.adempiere.model.copyRecord.CopyRecordSupportChildInfo;
import org.compiere.model.GridField;
import org.compiere.model.I_C_Order;
import org.compiere.model.I_C_OrderLine;
import org.compiere.model.PO;

import com.google.common.collect.ImmutableList;

/**
 * @author Cristina Ghita, METAS.RO
 *         version for copy an order
 */
public class MOrderPOCopyRecordSupport extends GeneralCopyRecordSupport
{
	private static final List<String> COLUMNNAMES_ToCopyDirectly = ImmutableList.of(
			I_C_Order.COLUMNNAME_PreparationDate // task 09000
			);

	@Override
	public List<CopyRecordSupportChildInfo> getSuggestedChildren(final PO po)
	{
		return super.getSuggestedChildren(po)
				.stream()
				.filter(childInfo -> I_C_OrderLine.Table_Name.equals(childInfo.getTableName()))
				.collect(ImmutableList.toImmutableList());
	}

	@Override
	public Object getValueToCopy(final PO to, final PO from, final String columnName)
	{
		if (COLUMNNAMES_ToCopyDirectly.contains(columnName))
		{
			return from.get_Value(columnName);
		}

		//
		// Fallback to super:
		return super.getValueToCopy(to, from, columnName);
	}

	@Override
	public Object getValueToCopy(final GridField gridField)
	{
		if (COLUMNNAMES_ToCopyDirectly.contains(gridField.getColumnName()))
		{
			return gridField.getValue();
		}

		//
		// Fallback to super:
		return super.getValueToCopy(gridField);
	}

}
