/*
 * Copyright (c) 2018 Ahome' Innovation Technologies. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ait.lienzo.client.core.shape.wires.handlers.impl;

import com.ait.lienzo.client.core.shape.Node;
import com.ait.lienzo.client.core.shape.Shape;
import com.ait.lienzo.client.core.shape.wires.BackingColorMapUtils;
import com.ait.lienzo.client.core.shape.wires.MagnetManager;
import com.ait.lienzo.client.core.shape.wires.WiresConnection;
import com.ait.lienzo.client.core.shape.wires.WiresConnector;
import com.ait.lienzo.client.core.shape.wires.WiresMagnet;
import com.ait.lienzo.client.core.shape.wires.WiresManager;
import com.ait.lienzo.client.core.shape.wires.WiresShape;
import com.ait.lienzo.client.core.shape.wires.handlers.WiresConnectionControl;
import com.ait.lienzo.client.core.types.ImageData;
import com.ait.lienzo.client.core.types.Point2D;
import com.ait.lienzo.client.core.util.ScratchPad;
import com.ait.tooling.nativetools.client.collection.NFastStringMap;

public class WiresConnectionControlImpl implements WiresConnectionControl
{
    private final WiresConnector              m_connector;

    private final WiresManager                m_manager;

    private final boolean                     m_head;

    private ImageData                         m_shapesBacking;

    private ImageData                         m_magnetsBacking;

    private MagnetManager.Magnets             m_magnets;

    private double                            m_startX;

    private double                            m_startY;

    private Point2D                           m_adjust;

    private String                            m_colorKey;

    private WiresMagnet                       m_initial_magnet;

    private WiresMagnet                       m_current_magnet;

    private boolean                           m_initialAutoConnect;

    private final NFastStringMap<WiresShape>  m_shape_color_map  = new NFastStringMap<>();

    private final NFastStringMap<WiresMagnet> m_magnet_color_map = new NFastStringMap<>();

    public WiresConnectionControlImpl(final WiresConnector connector, final boolean isHeadNotTail, final WiresManager wiresManager)
    {
        m_connector = connector;
        m_manager = wiresManager;
        m_head = isHeadNotTail;
        m_adjust = new Point2D(0, 0);
        m_initial_magnet = null;
        m_current_magnet = null;
    }

    @Override
    public void onMoveStart(final double x, final double y)
    {
        final Point2D points = getControlNode().getComputedLocation();

        m_startX = points.getX();

        m_startY = points.getY();

        final ScratchPad scratch = m_manager.getLayer().getLayer().getScratchPad();

        m_shapesBacking = BackingColorMapUtils.drawShapesToBacking(m_manager.getLayer().getChildShapes(), scratch, null, m_shape_color_map);

        m_connector.getLine().getOverLayer().getContext().createImageData(m_shapesBacking);

        final WiresConnection connection = getConnection();

        m_initialAutoConnect = connection.isAutoConnection();

        connection.setAutoConnection(false); // set it to false while dragging

        m_initial_magnet = connection.getMagnet();

        if (null != m_initial_magnet)
        {
            m_magnets = connection.getMagnet().getMagnets();

            m_magnetsBacking = m_manager.getMagnetManager().drawMagnetsToBack(m_magnets, m_shape_color_map, m_magnet_color_map, scratch);
        }
        // always null when drag start and reset the offsets (they may already be 0)

        connection.setMagnet(null);

        connection.setXOffset(0);

        connection.setYOffset(0);

        final String colorKey = BackingColorMapUtils.findColorAtPoint(m_shapesBacking, (int) m_startX, (int) m_startY);

        checkAllowAndShowMagnets(colorKey);
    }

    @Override
    public boolean onMoveComplete()
    {
        final boolean accept = makeAndUpdateSpecialConnections();

        if (!accept)
        {
            getConnection().setAutoConnection(m_initialAutoConnect).setMagnet(m_initial_magnet).getConnector().updateForSpecialConnections(false);
        }
        if (m_magnets != null)
        {
            m_magnets.hide();
        }
        m_shapesBacking = null;// uses lots of memory, so let it GC

        m_magnetsBacking = null;// uses lots of memory, so let it GC

        m_magnets = null;// if this is not nulled, the Mangets reference could stop Magnets being GC, when not used anywhere else

        m_colorKey = null;

        m_current_magnet = null;

        m_initial_magnet = null;

        m_shape_color_map.clear();

        m_magnet_color_map.clear();

        return accept;
    }

    private boolean makeAndUpdateSpecialConnections()
    {
        final WiresConnection connection = getConnection();

        WiresShape shape = null;

        // shape remains null, if the connection is either not connecting to a magnet or not over the body of a shape.

        if (m_current_magnet != null)
        {
            shape = m_current_magnet.getMagnets().getWiresShape();

            connection.setAutoConnection(false);
        }
        else
        {
            if (m_colorKey != null)
            {
                shape = m_shape_color_map.get(m_colorKey);

                if ((shape != null) && (shape.getMagnets() != null))
                {
                    // no magnet is selected, but if we are over a shape, then auto connect

                    connection.setAutoConnection(true);
                }
                else
                {
                    connection.setAutoConnection(false);
                }
            }
            else
            {
                connection.setAutoConnection(false);
            }
        }
        return allowedMagnetAndUpdateAutoConnections(connection, m_head, shape, m_current_magnet, true);
    }

    public static boolean allowedMagnetAndUpdateAutoConnections(final WiresConnection connection, final boolean isHead, final WiresShape shape, final WiresMagnet currentMagnet, final boolean applyAccept)
    {
        final WiresConnector connector = connection.getConnector();

        // shape can be null, to see if a connection can be unconnected to a magnet

        boolean accept;

        WiresShape headS;

        WiresShape tailS;

        if (isHead)
        {
            accept = connector.getConnectionAcceptor().headConnectionAllowed(connection, shape);

            headS = shape;

            tailS = (connector.getTailConnection().getMagnet() != null) ? connector.getTailConnection().getMagnet().getMagnets().getWiresShape() : null;
        }
        else
        {
            accept = connector.getConnectionAcceptor().tailConnectionAllowed(connection, shape);

            headS = (connector.getHeadConnection().getMagnet() != null) ? connector.getHeadConnection().getMagnet().getMagnets().getWiresShape() : null;

            tailS = shape;
        }
        if (applyAccept && accept)
        {
            accept = accept && acceptMagnetAndUpdateAutoConnection(connection, isHead, headS, tailS, currentMagnet);

            if (!accept)
            {
                throw new RuntimeException("This should never happen, acceptors should not fail if the alled passed. Added for defensive programming checking");
            }
        }
        return accept;
    }

    public static boolean acceptMagnetAndUpdateAutoConnection(final WiresConnection connection, final boolean isHead, final WiresShape headS, final WiresShape tailS, final WiresMagnet currentMagnet)
    {
        final WiresConnector connector = connection.getConnector();

        boolean accept = true;

        // Only set the current magnet, if auto connection is false

        final boolean isAuto = connection.isAutoConnection();

        if (!isAuto)
        {
            // m_current_magnet could also be null, and it's seeing if that's accepted
            // technically all connections have been checked and allowed, but for consistency and notifications will be rechecked via acceptor

            if (isHead)
            {
                accept = accept && connector.getConnectionAcceptor().acceptHead(connection, currentMagnet);
            }
            else
            {
                accept = accept && connector.getConnectionAcceptor().acceptTail(connection, currentMagnet);
            }
            if (accept)
            {
                // Set the magnet on the current connection
                // magnet could also be null

                connection.setMagnet(currentMagnet);
            }
        }
        if (accept)
        {
            // can be used during drag, as we know the current connection will have a null shape
            // this will cause the other side to be updated

            accept = accept && connector.updateForAutoConnections(headS, tailS, isAuto);

            connector.updateForCenterConnection();
        }
        return accept;
    }

    @Override
    public Point2D getAdjust()
    {
        return m_adjust;
    }

    @Override
    public boolean onMove(final double dx, final double dy)
    {
        final Point2D dxy = new Point2D(dx, dy);

        m_adjust = new Point2D(0, 0);

        // this is redetermined on each drag adjust

        m_current_magnet = null;

        final int x = (int) (m_startX + dxy.getX());

        final int y = (int) (m_startY + dxy.getY());

        final String colorKey = BackingColorMapUtils.findColorAtPoint(m_shapesBacking, x, y);

        if ((m_colorKey != null) && (colorKey != null) && !colorKey.equals(m_colorKey))
        {
            // this can happen when the mouse moves from an outer shape to an inner shape, or vice-versa
            // hide and null, and it'll show for the new.

            if (null != m_magnets)
            {
                m_magnets.hide();
            }
            m_magnets = null;

            m_colorKey = null;
        }
        boolean isAllowed = true;

        if (m_magnets == null)
        {
            isAllowed = checkAllowAndShowMagnets(colorKey);
        }
        // can be used during drag, as we know the current connection will have a null shape
        // this will cause the other side to be updated

        m_connector.updateForSpecialConnections(false);

        if (isAllowed)
        {
            if (null != m_magnets)
            {
                final String magnetColorKey = BackingColorMapUtils.findColorAtPoint(m_magnetsBacking, x, y);

                if (magnetColorKey == null)
                {
                    if (null != m_magnets)
                    {
                        m_magnets.hide();
                    }
                    m_magnets = null;

                    m_colorKey = null;
                }
                else
                {
                    // Take into account that it can be null, when over the main shape, instead of a magnet

                    final WiresMagnet potentialMagnet = m_magnet_color_map.get(magnetColorKey);

                    if ((m_connector.getHeadConnection().getMagnet() != potentialMagnet) && (m_connector.getTailConnection().getMagnet() != potentialMagnet))
                    {
                        // make sure we don't add a connection's head and tail to the same magnet

                        m_current_magnet = potentialMagnet;
                    }
                    else if (potentialMagnet == null)
                    {
                        m_current_magnet = null;
                    }
                }
            }
            if (null != m_current_magnet)
            {
                final Shape<?> control = m_current_magnet.getControl().asShape();

                if (control != null)
                {
                    // If there is a control, snap to it

                    final Point2D absControl = control.getComputedLocation();

                    final double targetX = absControl.getX();

                    final double targetY = absControl.getY();

                    final double tx = targetX - m_startX - dxy.getX();

                    final double ty = targetY - m_startY - dxy.getY();

                    if ((tx != 0) || (ty != 0))
                    {
                        m_adjust = new Point2D(dxy.getX() + tx, dxy.getY() + ty);
                    }
                }
            }
            return true;
        }
        return false;
    }

    private boolean checkAllowAndShowMagnets(final String colorKey)
    {
        final WiresShape prim = null != colorKey ? m_shape_color_map.get(colorKey) : null;

        m_colorKey = colorKey;

        if (isConnectionAllowed(prim))
        {
            showMagnets(prim);

            return true;
        }
        return false;
    }

    private boolean isConnectionAllowed(final WiresShape prim)
    {
        if (m_head)
        {
            return m_connector.getConnectionAcceptor().headConnectionAllowed(m_connector.getHeadConnection(), prim);
        }
        else
        {
            return m_connector.getConnectionAcceptor().tailConnectionAllowed(m_connector.getTailConnection(), prim);
        }
    }

    private void showMagnets(final WiresShape prim)
    {
        m_magnets = null != prim ? prim.getMagnets() : null;

        if (m_magnets != null)
        {
            m_magnets.show();

            final ScratchPad scratch = m_manager.getLayer().getLayer().getScratchPad();

            m_magnetsBacking = m_manager.getMagnetManager().drawMagnetsToBack(m_magnets, m_shape_color_map, m_magnet_color_map, scratch);
        }
    }

    private WiresConnection getConnection()
    {
        if (m_head)
        {
            return m_connector.getHeadConnection();
        }
        else
        {
            return m_connector.getTailConnection();
        }
    }

    private Node<?> getControlNode()
    {
        return (Node<?>) (m_head ? m_connector.getHeadConnection().getControl() : m_connector.getTailConnection().getControl());
    }
}
