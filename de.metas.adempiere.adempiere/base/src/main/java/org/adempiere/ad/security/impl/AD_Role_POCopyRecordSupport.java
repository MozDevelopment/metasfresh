package org.adempiere.ad.security.impl;

import java.sql.Timestamp;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Properties;

import org.adempiere.model.copyRecord.GeneralCopyRecordSupport;
import org.adempiere.util.Check;
import org.adempiere.util.Services;
import org.adempiere.util.api.IMsgBL;
import org.compiere.model.I_AD_Role;
import org.compiere.model.MUser;
import org.compiere.model.PO;
import org.compiere.util.Env;

/*
 * #%L
 * de.metas.adempiere.adempiere.base
 * %%
 * Copyright (C) 2016 metas GmbH
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

public class AD_Role_POCopyRecordSupport extends GeneralCopyRecordSupport
{
	private static final String MSG_AD_Role_Name_Unique = "AD_Role_Unique_Name";

	@Override
	public void updateSpecialColumnNames(final PO to)
	{
		final String roleName = buildUniqueRoleName(to);
		if (!Check.isEmpty(roleName, true))
		{
			to.set_Value(I_AD_Role.COLUMNNAME_Name, roleName);
		}
		else
		{
			super.updateSpecialColumnNames(to);
		}
	}

	private String buildUniqueRoleName(final PO rolePO)
	{
		final IMsgBL msgBL = Services.get(IMsgBL.class);

		final String timestampStr;
		{
			final Format formatter = new SimpleDateFormat("yyyyMMdd:HH:mm:ss");
			final Timestamp timestamp = new Timestamp(System.currentTimeMillis());
			timestampStr = formatter.format(timestamp);
		}

		final Properties ctx = Env.getCtx();
		final String name = MUser.getNameOfUser(Env.getAD_User_ID(ctx));
		final String language = Env.getAD_Language(ctx);
		// Create the name using the text from the specific AD_Message.
		final String roleName = msgBL.getMsg(language, MSG_AD_Role_Name_Unique, new String[] { name, timestampStr });
		return roleName;
	}
}
