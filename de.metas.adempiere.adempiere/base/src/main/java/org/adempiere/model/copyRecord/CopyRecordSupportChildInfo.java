/**
 *
 */
package org.adempiere.model.copyRecord;

import de.metas.i18n.ITranslatableString;
import lombok.Builder;
import lombok.Value;

/**
 * @author Cristina Ghita, METAS.RO
 *
 */
@Builder
@Value
public class CopyRecordSupportChildInfo
{
	private final ITranslatableString name;

	private final String tableName;
	private final String linkColumnName;

	private final String parentTableName;
	private final String parentColumnName;
}
