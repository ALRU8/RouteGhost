package org.json;
public class JSONArray {
  public JSONArray put(Object a)throws JSONException {return this;}
  public double getDouble(int x)throws JSONException{return 0;}
  public JSONArray getJSONArray(int x)throws JSONException{return this;}
  public JSONObject getJSONObject(int x)throws JSONException{return new JSONObject();}
  public int length(){return 0;}
}
