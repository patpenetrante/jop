/*
 * Copyright (c) 2007,2008, Stefan Hepp
 *
 * This file is part of JOPtimizer.
 *
 * JOPtimizer is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * JOPtimizer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.jopdesign.libgraph.struct.bcel;

import com.jopdesign.libgraph.struct.AppClassLoader;
import com.jopdesign.libgraph.struct.AppStruct;
import com.jopdesign.libgraph.struct.ClassInfo;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.util.ClassPath;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * @author Stefan Hepp, e0026640@student.tuwien.ac.at
 */
public class BcelClassLoader implements AppClassLoader {

    private ClassPath classPath;

    public BcelClassLoader() {
        classPath = new ClassPath(".");
    }

    public String getClassPath() {
        return classPath.toString();
    }

    public void setClassPath(String path) {
        classPath = new ClassPath(path);
    }

    public ClassInfo createClassInfo(AppStruct appStruct, String className) throws IOException {

        JavaClass jc = createJavaClass(className);
        if ( jc == null ) {
            return null;
        }

        return new BcelClassInfo(appStruct, jc);
    }

    /**
     * create Bcel JavaClasses from a list of classnames using the configured classpath.
     *
     * @param classNames a set of FQ-classnames to load.
     * @return a collection of Bcel-JavaClasses containing the given classes.
     * @throws IOException if reading a class fails.
     */
    public Collection createJavaClasses(Set classNames) throws IOException {
        List jc = new LinkedList();

        Iterator i = classNames.iterator();
        for (int nr=0; i.hasNext(); ++nr) {
            String clname = (String) i.next();
            jc.add( createJavaClass(clname) );
        }

        return jc;
    }

    /**
     * create a Bcel JavaClass from a classname using the configured classpath.
     *
     * @param className the name of the class to load.
     * @return the bcel class.
     * @throws IOException if reading the class fails.
     */
    public JavaClass createJavaClass(String className) throws IOException {
        InputStream is = classPath.getInputStream(className);
        return new ClassParser(is, className).parse();
    }

}
