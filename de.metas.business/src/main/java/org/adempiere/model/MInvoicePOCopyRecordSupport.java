/**
 *
 */
package org.adempiere.model;

import java.util.List;

import org.adempiere.model.copyRecord.GeneralCopyRecordSupport;
import org.adempiere.model.copyRecord.CopyRecordSupportChildInfo;
import org.compiere.model.I_C_InvoiceLine;
import org.compiere.model.PO;

import com.google.common.collect.ImmutableList;

/**
 * @author Cristina Ghita, METAS.RO
 *         version for copy an invoice
 */
public class MInvoicePOCopyRecordSupport extends GeneralCopyRecordSupport
{
	@Override
	public List<CopyRecordSupportChildInfo> getSuggestedChildren(final PO po)
	{
		return super.getSuggestedChildren(po)
				.stream()
				.filter(childInfo -> I_C_InvoiceLine.Table_Name.equals(childInfo.getTableName()))
				.collect(ImmutableList.toImmutableList());
	}
}
