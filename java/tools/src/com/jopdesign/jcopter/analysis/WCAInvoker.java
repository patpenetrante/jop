/*
 * This file is part of JOP, the Java Optimized Processor
 *   see <http://www.jopdesign.com/>
 *
 * Copyright (C) 2011, Stefan Hepp (stefan@stefant.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.jopdesign.jcopter.analysis;

import com.jopdesign.common.MethodInfo;
import com.jopdesign.common.code.CallGraph;
import com.jopdesign.jcopter.JCopter;

import java.util.Collection;

/**
 * @author Stefan Hepp (stefan@stefant.org)
 */
public class WCAInvoker {
    private final JCopter jcopter;
    private final Collection<MethodInfo> wcaTargets;

    public WCAInvoker(JCopter jcopter, Collection<MethodInfo> wcaTargets) {
        this.jcopter = jcopter;
        this.wcaTargets = wcaTargets;
    }

    public JCopter getJcopter() {
        return jcopter;
    }

    public Collection<MethodInfo> getWcaTargets() {
        return wcaTargets;
    }

    public boolean isWCAMethod(MethodInfo method) {
        return false;
    }

    public Collection<CallGraph> getWCACallGraphs() {
        return null;
    }
}