/*
    Copyright 2015 Yasuaki Honda (yasuaki.honda@gmail.com)
    This file is part of MaximaOnAndroid.

    MaximaOnAndroid is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 2 of the License, or
    (at your option) any later version.

    MaximaOnAndroid is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Foobar.  If not, see <http://www.gnu.org/licenses/>.
 */

package jp.yhonda;

import android.os.Build;

public final class CpuArchitecture {
    static final String X86 = "x86";
    static final String ARM = "arm";
    static final String NOT_SUPPORTED = "not supported";
    static final String NOT_INITIALIZED = "not initialized";

    private static String cpuarch = NOT_INITIALIZED;

    private CpuArchitecture() {
    }

    public static String getCpuArchitecture() {
        return cpuarch;
    }

    public static void initCpuArchitecture() {
        String res = Build.CPU_ABI.toLowerCase();
        if (res.contains(X86)) {
            cpuarch = X86;
        } else if (res.contains(ARM)) {
            cpuarch = ARM;
        } else if (res.equals(NOT_SUPPORTED)) {
            cpuarch = NOT_SUPPORTED;
        }
    }

    public static String getMaximaFile() {
        if (cpuarch.startsWith("not")) {
            return cpuarch;
        }
        if (cpuarch.equals(X86)) {
            return ("maxima.x86.pie");
        } else if (cpuarch.equals(ARM)) {
            return ("maxima.pie");
        }
        return NOT_SUPPORTED;
    }
}
