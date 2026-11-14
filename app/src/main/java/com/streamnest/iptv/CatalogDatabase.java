package com.streamnest.iptv;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;

public class CatalogDatabase extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "streamnest_catalog.db";
    private static final int DATABASE_VERSION = 2;

    private static final String TABLE = "catalog";
    private static final String STATE = "catalog_state";
    private static final String REFRESH = "catalog_refresh";

    public static String makeProviderKey(String identity) {
        if (identity == null || identity.trim().isEmpty()) {
            throw new IllegalArgumentException("Provider identity is required");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(identity.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : hash) result.append(String.format("%02x", b & 0xff));
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash provider identity", e);
        }
    }

    public static class CatalogItem {
        public String type;
        public String itemId;
        public String name;
        public String streamRef;
        public String category;
        public String epgId;
        public String artworkUrl;
        public String extra;

        public CatalogItem(String type, String itemId, String name,
                String streamRef, String category, String epgId,
                String artworkUrl, String extra) {
            this.type = type;
            this.itemId = itemId;
            this.name = name;
            this.streamRef = streamRef;
            this.category = category;
            this.epgId = epgId;
            this.artworkUrl = artworkUrl;
            this.extra = extra;
        }
    }

    public CatalogDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " (" +
                "provider_key TEXT NOT NULL," +
                "type TEXT NOT NULL," +
                "generation INTEGER NOT NULL," +
                "item_id TEXT NOT NULL," +
                "name TEXT," +
                "stream_ref TEXT," +
                "category TEXT," +
                "epg_id TEXT," +
                "artwork_url TEXT," +
                "extra TEXT," +
                "PRIMARY KEY(provider_key,type,generation,item_id))");

        db.execSQL("CREATE INDEX catalog_lookup ON " + TABLE +
                "(provider_key,type,generation,category)");

        db.execSQL("CREATE TABLE " + STATE + " (" +
                "provider_key TEXT NOT NULL," +
                "type TEXT NOT NULL," +
                "active_generation INTEGER NOT NULL," +
                "PRIMARY KEY(provider_key,type))");

        db.execSQL("CREATE TABLE " + REFRESH + " (" +
                "refresh_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "provider_key TEXT NOT NULL," +
                "type TEXT NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLE + " RENAME TO catalog_old");
            db.execSQL("DROP INDEX IF EXISTS catalog_lookup");
            db.execSQL("CREATE TABLE " + TABLE + " (" +
                    "provider_key TEXT NOT NULL,type TEXT NOT NULL," +
                    "generation INTEGER NOT NULL,item_id TEXT NOT NULL," +
                    "name TEXT,stream_ref TEXT,category TEXT,epg_id TEXT," +
                    "artwork_url TEXT,extra TEXT," +
                    "PRIMARY KEY(provider_key,type,generation,item_id))");
            db.execSQL("INSERT INTO " + TABLE +
                    " (provider_key,type,generation,item_id,name,stream_ref," +
                    "category,epg_id,artwork_url,extra) " +
                    "SELECT provider_key,type,1,item_id,name,stream_ref," +
                    "category,epg_id,artwork_url,extra FROM catalog_old");
            db.execSQL("DROP TABLE catalog_old");
            db.execSQL("CREATE INDEX catalog_lookup ON " + TABLE +
                    "(provider_key,type,generation,category)");
            db.execSQL("CREATE TABLE " + STATE + " (" +
                    "provider_key TEXT NOT NULL,type TEXT NOT NULL," +
                    "active_generation INTEGER NOT NULL," +
                    "PRIMARY KEY(provider_key,type))");
            db.execSQL("INSERT INTO " + STATE +
                    " (provider_key,type,active_generation) " +
                    "SELECT DISTINCT provider_key,type,1 FROM " + TABLE);
            db.execSQL("CREATE TABLE " + REFRESH + " (" +
                    "refresh_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "provider_key TEXT NOT NULL,type TEXT NOT NULL)");
        }
    }

    public long beginRefresh(String providerKey, String type) {
        if (providerKey == null || providerKey.isEmpty()
                || type == null || type.isEmpty()) {
            throw new IllegalArgumentException("Provider and type required");
        }

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(TABLE,
                    "provider_key=? AND type=? AND generation IN " +
                    "(SELECT refresh_id FROM " + REFRESH +
                    " WHERE provider_key=? AND type=?)",
                    new String[]{providerKey, type, providerKey, type});

            db.delete(REFRESH, "provider_key=? AND type=?",
                    new String[]{providerKey, type});

            ContentValues values = new ContentValues();
            values.put("provider_key", providerKey);
            values.put("type", type);
            long refreshId = db.insertOrThrow(REFRESH, null, values);

            db.setTransactionSuccessful();
            return refreshId;
        } finally {
            db.endTransaction();
        }
    }

    public void insertBatch(String providerKey, String type,
            long generation, ArrayList<CatalogItem> items) {
        if (providerKey == null || type == null || items == null
                || generation <= 0) {
            throw new IllegalArgumentException("Invalid catalog batch");
        }

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            try (Cursor cursor = db.rawQuery(
                    "SELECT 1 FROM " + REFRESH +
                    " WHERE refresh_id=? AND provider_key=? AND type=?",
                    new String[]{String.valueOf(generation), providerKey, type})) {
                if (!cursor.moveToFirst()) {
                    throw new IllegalStateException("Refresh was superseded");
                }
            }

            for (CatalogItem item : items) {
                if (item == null || item.itemId == null
                        || item.itemId.trim().isEmpty()
                        || !type.equals(item.type)) {
                    throw new IllegalArgumentException("Invalid catalog item");
                }

                ContentValues values = new ContentValues();
                values.put("provider_key", providerKey);
                values.put("type", type);
                values.put("generation", generation);
                values.put("item_id", item.itemId);
                values.put("name", item.name);
                values.put("stream_ref", item.streamRef);
                values.put("category", item.category);
                values.put("epg_id", item.epgId);
                values.put("artwork_url", item.artworkUrl);
                values.put("extra", item.extra);
                db.insertOrThrow(TABLE, null, values);
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void finishRefresh(long refreshId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            String providerKey;
            String type;

            try (Cursor cursor = db.query(
                    REFRESH,
                    new String[]{"provider_key", "type"},
                    "refresh_id=?",
                    new String[]{String.valueOf(refreshId)},
                    null, null, null)) {
                if (!cursor.moveToFirst()) {
                    throw new IllegalStateException("Refresh not found");
                }
                providerKey = cursor.getString(0);
                type = cursor.getString(1);
            }

            try (Cursor count = db.rawQuery(
                    "SELECT COUNT(*) FROM " + TABLE +
                    " WHERE provider_key=? AND type=? AND generation=?",
                    new String[]{providerKey, type, String.valueOf(refreshId)})) {
                if (!count.moveToFirst() || count.getLong(0) == 0) {
                    throw new IllegalStateException("Cannot activate an empty catalog");
                }
            }

            ContentValues state = new ContentValues();
            state.put("provider_key", providerKey);
            state.put("type", type);
            state.put("active_generation", refreshId);

            db.insertWithOnConflict(
                    STATE, null, state, SQLiteDatabase.CONFLICT_REPLACE);

            db.delete(TABLE,
                    "provider_key=? AND type=? AND generation<>?",
                    new String[]{providerKey, type, String.valueOf(refreshId)});

            db.delete(REFRESH, "refresh_id=?",
                    new String[]{String.valueOf(refreshId)});

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void abandonRefresh(long refreshId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            String providerKey = null;
            String type = null;

            try (Cursor cursor = db.query(
                    REFRESH,
                    new String[]{"provider_key", "type"},
                    "refresh_id=?",
                    new String[]{String.valueOf(refreshId)},
                    null, null, null)) {
                if (cursor.moveToFirst()) {
                    providerKey = cursor.getString(0);
                    type = cursor.getString(1);
                }
            }

            if (providerKey != null) {
                db.delete(TABLE,
                        "provider_key=? AND type=? AND generation=?",
                        new String[]{providerKey, type,
                                String.valueOf(refreshId)});
            }

            db.delete(REFRESH, "refresh_id=?",
                    new String[]{String.valueOf(refreshId)});

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }


    public long getActiveGeneration(String providerKey, String type) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(
                STATE,
                new String[]{"active_generation"},
                "provider_key=? AND type=?",
                new String[]{providerKey, type},
                null, null, null)) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0;
        }
    }

    public ArrayList<CatalogItem> getCatalogPage(
            String providerKey, String type, String category,
            int limit, int offset) {
        ArrayList<CatalogItem> items = new ArrayList<>();
        if (limit <= 0 || offset < 0) return items;

        long generation = getActiveGeneration(providerKey, type);
        if (generation <= 0) return items;

        SQLiteDatabase db = getReadableDatabase();
        String selection = "provider_key=? AND type=? AND generation=?";
        ArrayList<String> args = new ArrayList<>();
        args.add(providerKey);
        args.add(type);
        args.add(String.valueOf(generation));

        if (category != null) {
            selection += " AND category=?";
            args.add(category);
        }

        try (Cursor cursor = db.query(
                TABLE,
                new String[]{"type", "item_id", "name", "stream_ref",
                        "category", "epg_id", "artwork_url", "extra"},
                selection,
                args.toArray(new String[0]),
                null, null,
                "rowid",
                limit + " OFFSET " + offset)) {
            while (cursor.moveToNext()) {
                items.add(new CatalogItem(
                        cursor.getString(0), cursor.getString(1),
                        cursor.getString(2), cursor.getString(3),
                        cursor.getString(4), cursor.getString(5),
                        cursor.getString(6), cursor.getString(7)));
            }
        }
        return items;
    }

    public int getCatalogCount(
            String providerKey, String type, String category) {
        long generation = getActiveGeneration(providerKey, type);
        if (generation <= 0) return 0;

        SQLiteDatabase db = getReadableDatabase();
        String selection = "provider_key=? AND type=? AND generation=?";
        ArrayList<String> args = new ArrayList<>();
        args.add(providerKey);
        args.add(type);
        args.add(String.valueOf(generation));

        if (category != null) {
            selection += " AND category=?";
            args.add(category);
        }

        try (Cursor cursor = db.query(
                TABLE, new String[]{"COUNT(*)"}, selection,
                args.toArray(new String[0]), null, null, null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }


    public ArrayList<String> getCatalogCategories(
            String providerKey, String type) {
        ArrayList<String> categories = new ArrayList<>();
        long generation = getActiveGeneration(providerKey, type);
        if (generation <= 0) return categories;

        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(
                true, TABLE, new String[]{"category"},
                "provider_key=? AND type=? AND generation=? AND category IS NOT NULL AND category<>' '",
                new String[]{providerKey, type, String.valueOf(generation)},
                null, null, "category COLLATE NOCASE", null)) {
            while (cursor.moveToNext()) {
                String category = cursor.getString(0);
                if (category != null && !category.trim().isEmpty()) {
                    categories.add(category);
                }
            }
        }
        return categories;
    }

}
