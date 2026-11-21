package org.json;
public class JSONObject {
  public JSONObject(){} public JSONObject(String s)throws JSONException{}
  public JSONObject put(String k,Object o)throws JSONException{return this;}
  public String getString(String k)throws JSONException{return "";}
  public int getInt(String k)throws JSONException{return 0;}
  public long getLong(String k)throws JSONException{return 0;}
  public JSONArray getJSONArray(String k)throws JSONException{return new JSONArray();}
  public JSONArray optJSONArray(String k){return new JSONArray();}
  public String optString(String k,String d){return d;}
  public String optString(String k){return "";}
  public int optInt(String k,int d){return d;}
  public boolean optBoolean(String k,boolean d){return d;}
  public long optLong(String k,long d){return d;}
  public JSONObject getJSONObject(String k)throws JSONException{return new JSONObject();}
  public JSONObject optJSONObject(String k){return new JSONObject();}
  public boolean has(String k){return false;}
}
