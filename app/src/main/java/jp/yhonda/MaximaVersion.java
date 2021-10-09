/*
    Copyright 2012, 2013, Yasuaki Honda (yasuaki.honda@gmail.com)
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
    along with MaximaOnAndroid.  If not, see <http://www.gnu.org/licenses/>.
 */

package jp.yhonda;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.Context;

public final class MaximaVersion {
	private int major, minor, patch;

	MaximaVersion() {
		major = 5;
		minor = 27;
		patch = 0;
	}

	MaximaVersion(int major, int minor, int patch) {
		this.major = major;
		this.minor = minor;
		this.patch = patch;
	}

	public void loadVersFromSharedPrefs(Context context) {
		SharedPreferences pref = context.getSharedPreferences("maxima",
				Context.MODE_PRIVATE);
		major = pref.getInt("major", 5);
		minor = pref.getInt("minor", 27);
		patch = pref.getInt("patch", 0);
	}

	public void saveVersToSharedPrefs(Context context) {
		Editor ed = context
				.getSharedPreferences("maxima", Context.MODE_PRIVATE).edit();
		ed.putInt("major", major);
		ed.putInt("minor", minor);
		ed.putInt("patch", patch);
		ed.apply();
	}

	public long versionInteger() {
		return ((long) major) * (1000 * 1000) + ((long) minor) * 1000 + ((long) patch);
	}

	public String versionString() {
		return major + "." + minor + "." + patch;
	}

}
