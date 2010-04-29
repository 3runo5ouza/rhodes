/*
 ============================================================================
 Author	    : Dmitry Moskalchuk
 Version	: 1.5
 Copyright  : Copyright (C) 2008 Rhomobile. All rights reserved.

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ============================================================================
 */
package com.rhomobile.rhodes.mainview;

import java.util.Map;
import java.util.Vector;

import com.rhomobile.rhodes.Logger;
import com.rhomobile.rhodes.Rhodes;
import com.rhomobile.rhodes.RhodesInstance;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.TabHost;
import android.widget.TabWidget;

public class TabbedMainView implements MainView {
	
	private static final String TAG = "TabbedMainView";
	
	private TabHost host;
	private Vector<TabData> tabs;
	
	private static class TabData {
		public WebView view;
		public String url;
		public boolean reload;
		public boolean loaded;
		
		public TabData() {
			loaded = false;
		}
	};
	
	private static class TabViewFactory implements TabHost.TabContentFactory {
		
		private TabData data;
		
		public TabViewFactory(TabData d) {
			data = d;
		}
		
		public View createTabContent(String tag) {
			return data.view;
		}
		
	};
	
	private WebView getWebView(int index) {
		TabData data = tabs.elementAt(index);
		return data.view;
	}
	
	@SuppressWarnings("unchecked")
	public TabbedMainView(Vector<Object> params) {
		Rhodes r = RhodesInstance.getInstance();
		
		int size = params.size();
		
		host = new TabHost(r);
		host.setId(Rhodes.RHO_MAIN_VIEW);
		
		tabs = new Vector<TabData>(size);
		
		TabWidget tabWidget = new TabWidget(r);
		tabWidget.setId(android.R.id.tabs);
		TabHost.LayoutParams lpt = new TabHost.LayoutParams(LayoutParams.FILL_PARENT,
				LayoutParams.WRAP_CONTENT, Gravity.TOP);
		host.addView(tabWidget, lpt);
		
		FrameLayout frame = new FrameLayout(r);
		frame.setId(android.R.id.tabcontent);
		FrameLayout.LayoutParams lpf = new FrameLayout.LayoutParams(LayoutParams.FILL_PARENT,
				LayoutParams.FILL_PARENT, Gravity.BOTTOM);
		// TODO: detect tab widget height and use it here instead of hardcoded value
		lpf.setMargins(0, 64, 0, 0);
		host.addView(frame, lpf);
		
		host.setup();
		
		host.setOnTabChangedListener(new TabHost.OnTabChangeListener() {
			
			public void onTabChanged(String tabId) {
				try {
					int index = Integer.parseInt(tabId);
					TabData data = tabs.elementAt(index);
					if (data.reload || !data.loaded) {
						getWebView(index).loadUrl(data.url);
						data.loaded = true;
					}
				}
				catch (NumberFormatException e) {
					Logger.E(TAG, e);
				}
			}
		});
		
		TabHost.TabSpec spec;
		
		String rootPath = RhodesInstance.getInstance().getRootPath() + "/apps/";

		for (int i = 0; i < size; ++i) {
			Object param = params.elementAt(i);
			if (!(param instanceof Map<?,?>))
				throw new IllegalArgumentException("Hash expected");
			
			Map<Object, Object> hash = (Map<Object, Object>)param;
			
			Object labelObj = hash.get("label");
			if (labelObj == null || !(labelObj instanceof String))
				throw new IllegalArgumentException("'label' should be String");
			
			Object actionObj = hash.get("action");
			if (actionObj == null || !(actionObj instanceof String))
				throw new IllegalArgumentException("'action' should be String");
			
			String label = (String)labelObj;
			String action = r.normalizeUrl((String)actionObj);
			String icon = null;
			boolean reload = false;
			
			Object iconObj = hash.get("icon");
			if (iconObj != null && (iconObj instanceof String))
				icon = rootPath + (String)iconObj;
			
			Object reloadObj = hash.get("reload");
			if (reloadObj != null && (reloadObj instanceof String))
				reload = ((String)reloadObj).equalsIgnoreCase("true");
			
			spec = host.newTabSpec(Integer.toString(i));
			
			// Set label and icon
			BitmapDrawable drawable = null;
			if (icon != null) {
				Bitmap bitmap = BitmapFactory.decodeFile(icon);
				if (bitmap != null)
					drawable = new BitmapDrawable(bitmap);
			}
			if (drawable == null)
				spec.setIndicator(label);
			else
				spec.setIndicator(label, drawable);
			
			// Set view factory
			WebView view = r.createWebView();
			TabData data = new TabData();
			data.view = view;
			data.url = action;
			data.reload = reload;
			
			TabViewFactory factory = new TabViewFactory(data);
			spec.setContent(factory);
			
			tabs.addElement(data);
			host.addTab(spec);
		}
	}

	public View getView() {
		return host;
	}
	
	public void back(int index) {
		getWebView(index).goBack();
	}
	
	public void forward(int index) {
		getWebView(index).goForward();
	}
	
	public void navigate(String url, int index) {
		getWebView(index).loadUrl(url);
	}
	
	public void reload(int index) {
		getWebView(index).reload();
	}
	
	public String currentLocation(int index) {
		return getWebView(index).getUrl();
	}

	public void switchTab(int index) {
		host.setCurrentTab(index);
	}
	
	public int activeTab() {
		return host.getCurrentTab();
	}

	public void loadData(String data, int index) {
		getWebView(index).loadData(data, "text/html", "utf-8");
	}

}
