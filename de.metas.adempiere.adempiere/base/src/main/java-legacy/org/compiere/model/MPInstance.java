/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 1999-2006 ComPiere, Inc. All Rights Reserved.                *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * ComPiere, Inc., 2620 Augustine Dr. #245, Santa Clara, CA 95054, USA        *
 * or via info@compiere.org or http://www.compiere.org/license.html           *
 *****************************************************************************/
package org.compiere.model;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;

import org.adempiere.ad.security.IUserRolePermissions;
import org.adempiere.ad.service.IADPInstanceDAO;
import org.adempiere.ad.service.IADProcessDAO;
import org.adempiere.ad.service.IDeveloperModeBL;
import org.adempiere.ad.trx.api.ITrx;
import org.adempiere.exceptions.AdempiereException;
import org.adempiere.exceptions.DBException;
import org.adempiere.model.InterfaceWrapperHelper;
import org.adempiere.util.Check;
import org.adempiere.util.Services;
import org.compiere.process.ProcessInfo;
import org.compiere.util.DB;
import org.compiere.util.Env;

import com.google.common.collect.ImmutableList;

/**
 *  Process Instance Model
 *
 *  @author Jorg Janke
 *  @version $Id: MPInstance.java,v 1.3 2006/07/30 00:58:36 jjanke Exp $
 * 
 * @author Teo Sarca, www.arhipac.ro
 * 		<li>FR [ 2818478 ] Introduce MPInstance.createParameter helper method
 * 			https://sourceforge.net/tracker/?func=detail&aid=2818478&group_id=176962&atid=879335
 */
public class MPInstance extends X_AD_PInstance
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 209806970824523840L;

	/**
	 * 	Standard Constructor
	 *	@param ctx context
	 *	@param AD_PInstance_ID instance or 0
	 *	@param trxName_IGNORED no transaction support
	 */
	public MPInstance (Properties ctx, int AD_PInstance_ID, String trxName_IGNORED)
	{
		super (ctx, AD_PInstance_ID, ITrx.TRXNAME_None);

		// metas: WARN developer if he/she is loading the AD_PInstance using transactions (because that is not allowed)
		if (!Check.equals(ITrx.TRXNAME_None, trxName_IGNORED) && Services.get(IDeveloperModeBL.class).isEnabled())
		{
			final AdempiereException ex = new AdempiereException("AD_PInstance was loaded using trxName '" + trxName_IGNORED + "' while only '" + ITrx.TRXNAME_None + "' is allowed.");
			log.warn(ex.getLocalizedMessage(), ex);
		}
		
		//	New Process
		if (AD_PInstance_ID == 0)
		{
		//	setAD_Process_ID (0);	//	parent
		//	setRecord_ID (0);
			setIsProcessing (false);
		}
	}	//	MPInstance

	/**
	 * 	Load Constructor
	 *	@param ctx context
	 *	@param rs result set
	 *	@param ignored no transaction support
	 */
	public MPInstance (Properties ctx, ResultSet rs, String ignored)
	{
		super(ctx, rs, ITrx.TRXNAME_None);
	}	//	MPInstance

	/**
	 * Create Process Instance from Process and create parameters
	 *
	 * @param process process
	 * @param adTableId
	 * @param Record_ID Record
	 */
	public MPInstance (final I_AD_Process process, final int adTableId, final int Record_ID)
	{
		this(InterfaceWrapperHelper.getCtx(process), process, adTableId, Record_ID);
	}
	
	private MPInstance(final Properties ctx, final I_AD_Process process, final int adTableId, final int recordId)
	{
		this(ctx, 0, ITrx.TRXNAME_None);
		setAD_Process_ID(process.getAD_Process_ID());
		if (adTableId > 0)
		{
			setAD_Table_ID(adTableId);
		}
		setRecord_ID(recordId);
		setAD_User_ID(Env.getAD_User_ID(ctx));
		setAD_Role_ID(Env.getAD_Role_ID(ctx));

		DB.saveConstraints();
		try
		{
			DB.getConstraints().incMaxTrx(1).addAllowedTrxNamePrefix(ITrx.TRXNAME_PREFIX_LOCAL);

			saveEx();		// need to save for parameters // metas: use saveEx

			// Set Parameter Base Info
			int seqNo = 10; // metas
			for (final I_AD_Process_Para para : Services.get(IADProcessDAO.class).retrieveProcessParameters(process))
			{
				I_AD_PInstance_Para pip = new MPInstancePara(this, seqNo); // metas: use seqNo instead of para[i].getSeqNo()
				pip.setParameterName(para.getColumnName());
				pip.setInfo(para.getName());
				InterfaceWrapperHelper.save(pip);
				seqNo += 10; // metas
			}

		}
		finally
		{
			DB.restoreConstraints();
		}
	}	// MPInstance

	public MPInstance (final Properties ctx, ProcessInfo pi)
	{
		this(ctx, pi.getAD_Process_ID(), pi.getTable_ID(), pi.getRecord_ID());
		setWhereClause(pi.getWhereClause());
	}

	/**
	 * New Constructor
	 *
	 * @param ctx context
	 * @param AD_Process_ID Process ID
	 * @param adTableId
	 * @param Record_ID record
	 */
	public MPInstance (final Properties ctx, final int AD_Process_ID, final int AD_Table_ID, final int Record_ID)
	{
		this(ctx, 0, ITrx.TRXNAME_None);
		setAD_Process_ID (AD_Process_ID);
		if(AD_Table_ID > 0)
		{
			setAD_Table_ID(AD_Table_ID);
		}
		setRecord_ID (Record_ID);
		setAD_User_ID(Env.getAD_User_ID(ctx));
		setAD_Role_ID(Env.getAD_Role_ID(ctx));
		setIsProcessing (false);
	}	//	MPInstance
	

	/**	Parameters						*/
	private List<I_AD_PInstance_Para> m_parameters = null;

	/**
	 * 	Get Parameters
	 *	@return parameter array
	 */
	public List<I_AD_PInstance_Para> getParameters()
	{
		if (m_parameters == null)
		{
			final IADPInstanceDAO adPInstanceDAO = Services.get(IADPInstanceDAO.class);
			final List<I_AD_PInstance_Para> parametersList = adPInstanceDAO.retrievePInstanceParams(getCtx(), getAD_PInstance_ID());
			m_parameters = ImmutableList.copyOf(parametersList);
		}
		return m_parameters;
	}	//	getParameters

	/**
	 *	Get Logs
	 *	@return logs
	 */
	public List<MPInstanceLog> getLog()
	{
		//	load it from DB
		final String sql = "SELECT * FROM AD_PInstance_Log WHERE AD_PInstance_ID=? ORDER BY Log_ID";
		final Object[] sqlParams = new Object[] { getAD_PInstance_ID() };
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			pstmt = DB.prepareStatement(sql, ITrx.TRXNAME_None);
			DB.setParameters(pstmt, sqlParams);
			pstmt.setInt(1, getAD_PInstance_ID());
			rs = pstmt.executeQuery();
			
			final ImmutableList.Builder<MPInstanceLog> logs = ImmutableList.builder();
			while (rs.next())
			{
				logs.add(new MPInstanceLog(rs));
			}
			
			return logs.build();
		}
		catch (SQLException e)
		{
			throw new DBException(e, sql, sqlParams);
		}
		finally
		{
			DB.close(rs, pstmt);
		}
	}

	/**
	 * 	Set AD_Process_ID.
	 * 	Check Role if process can be performed.
	 *	@param AD_Process_ID process
	 *  @throws AdempiereException if the current role can't access the process
	 */
	@Override
	public void setAD_Process_ID (int AD_Process_ID)
	{
		final IUserRolePermissions role = Env.getUserRolePermissions(getCtx());
		if (role.getAD_Role_ID() > 0)
		{
			final Boolean access = role.getProcessAccess(AD_Process_ID);
			if (access == null || !access.booleanValue())
			{
				throw new AdempiereException("Cannot access Process " + AD_Process_ID + " with role: " + role.getName());
			}
		}
		super.setAD_Process_ID (AD_Process_ID);
	}	//	setAD_Process_ID

	/**
	 * 	Set Record ID.
	 * 	direct internal record ID
	 * 	@param Record_ID record
	 **/
	@Override
	public void setRecord_ID (int Record_ID)
	{
		if (Record_ID < 0)
		{
			log.info("Set to 0 from " + Record_ID);
			Record_ID = 0;
		}
		set_ValueNoCheck (COLUMNNAME_Record_ID, Record_ID);
	}	//	setRecord_ID

	@Override
	public String toString ()
	{
		StringBuilder sb = new StringBuilder("MPInstance[")
			.append (get_ID())
			.append(",OK=").append(isOK());
		String msg = getErrorMsg();
		if (msg != null && msg.length() > 0)
			sb.append(msg);
		sb.append ("]");
		return sb.toString ();
	}	//	toString

	/** Result OK = 1			*/
	public static final int		RESULT_OK = 1;
	/** Result FALSE = 0		*/
	public static final int		RESULT_ERROR = 0;

	/**
	 * 	Is it OK
	 *	@return Result == OK
	 */
	private boolean isOK()
	{
		return getResult() == RESULT_OK;
	}	//	isOK
	
	/**
	 * 	After Save
	 *	@param newRecord new
	 *	@param success success
	 *	@return success
	 */
	@Override
	protected boolean afterSave (boolean newRecord, boolean success)
	{
		//	Update Statistics
		if (!newRecord 
			&& !isProcessing()
			&& is_ValueChanged(COLUMNNAME_IsProcessing))
		{
			long ms = System.currentTimeMillis() - getCreated().getTime();
			int seconds = (int)(ms / 1000);
			if (seconds < 1)
				seconds = 1;
			MProcess prc = MProcess.get(getCtx(), getAD_Process_ID());
			prc.addStatistics(seconds);
			if (prc.get_ID() != 0 && prc.save())
				log.debug("afterSave - Process Statistics updated Sec=" + seconds);
			else
				log.warn("afterSave - Process Statistics not updated");
		}
		return success;
	}	//	afterSave
}
