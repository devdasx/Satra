package dev.satra.wallet.data.db

object SatraDatabaseContract {
    const val DATABASE_NAME = "satra_wallet.db"
    const val DATABASE_VERSION = 4

    const val TABLE_SUPPORTED_NETWORKS = "supported_networks"
    const val TABLE_SUPPORTED_ASSETS = "supported_assets"
    const val TABLE_APP_SETTINGS = "app_settings"
    const val TABLE_ADDRESS_BOOK = "address_book_entries"
    const val TABLE_WALLETS = "wallets"
    const val TABLE_WALLET_ASSETS = "wallet_assets"
    const val TABLE_WALLET_ADDRESSES = "wallet_addresses"
    const val TABLE_WALLET_PRIVATE_KEYS = "wallet_private_keys"
    const val TABLE_WALLET_TRANSACTIONS = "wallet_transactions"
    const val TABLE_ASSET_MARKET_DATA = "asset_market_data"

    val createStatements = listOf(
        """
        CREATE TABLE $TABLE_SUPPORTED_NETWORKS (
            network_id TEXT NOT NULL PRIMARY KEY,
            display_name TEXT NOT NULL,
            family TEXT NOT NULL,
            native_symbol TEXT NOT NULL,
            native_decimals INTEGER NOT NULL,
            chain_id INTEGER,
            token_standard TEXT,
            is_layer2 INTEGER NOT NULL DEFAULT 0,
            is_enabled INTEGER NOT NULL DEFAULT 1,
            source TEXT NOT NULL
        )
        """,
        """
        CREATE TABLE $TABLE_SUPPORTED_ASSETS (
            asset_id TEXT NOT NULL PRIMARY KEY,
            symbol TEXT NOT NULL,
            name TEXT NOT NULL,
            network_id TEXT NOT NULL,
            asset_type TEXT NOT NULL,
            decimals INTEGER NOT NULL,
            token_standard TEXT,
            contract_address TEXT,
            is_stablecoin INTEGER NOT NULL DEFAULT 0,
            is_enabled INTEGER NOT NULL DEFAULT 1,
            sort_order INTEGER NOT NULL DEFAULT 0,
            source TEXT NOT NULL,
            FOREIGN KEY(network_id) REFERENCES $TABLE_SUPPORTED_NETWORKS(network_id)
                ON DELETE RESTRICT
        )
        """,
        """
        CREATE TABLE $TABLE_APP_SETTINGS (
            settings_id TEXT NOT NULL PRIMARY KEY,
            local_currency_code TEXT NOT NULL DEFAULT '$DEFAULT_LOCAL_CURRENCY_CODE',
            language_tag TEXT NOT NULL DEFAULT 'en',
            theme_preference TEXT NOT NULL DEFAULT 'System',
            haptics_enabled INTEGER NOT NULL DEFAULT 1,
            passcode_enabled INTEGER NOT NULL DEFAULT 0,
            passcode_hash TEXT,
            passcode_salt TEXT,
            passcode_length INTEGER,
            biometrics_enabled INTEGER NOT NULL DEFAULT 0,
            auto_lock_timeout_millis INTEGER NOT NULL DEFAULT 0,
            erase_wallet_enabled INTEGER NOT NULL DEFAULT 1,
            erase_wallet_attempt_limit INTEGER NOT NULL DEFAULT 10,
            failed_passcode_attempts INTEGER NOT NULL DEFAULT 0,
            notifications_news_enabled INTEGER NOT NULL DEFAULT 1,
            notifications_prices_enabled INTEGER NOT NULL DEFAULT 1,
            notifications_transactions_enabled INTEGER NOT NULL DEFAULT 1,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL,
            metadata_json TEXT NOT NULL DEFAULT '$EMPTY_JSON'
        )
        """,
        """
        CREATE TABLE $TABLE_ADDRESS_BOOK (
            entry_id TEXT NOT NULL PRIMARY KEY,
            label TEXT NOT NULL,
            network_id TEXT NOT NULL,
            address TEXT NOT NULL,
            notes TEXT,
            is_favorite INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL,
            metadata_json TEXT NOT NULL DEFAULT '$EMPTY_JSON',
            UNIQUE(network_id, address),
            FOREIGN KEY(network_id) REFERENCES $TABLE_SUPPORTED_NETWORKS(network_id) ON DELETE RESTRICT
        )
        """,
        """
        CREATE TABLE $TABLE_WALLETS (
            wallet_id TEXT NOT NULL PRIMARY KEY,
            wallet_name TEXT NOT NULL,
            wallet_type TEXT NOT NULL,
            wallet_key_type TEXT NOT NULL,
            wallet_key_material TEXT,
            wallet_key_fingerprint TEXT,
            wallet_key_derivation_path TEXT,
            passphrase TEXT,
            wallet_key_encryption_state TEXT NOT NULL DEFAULT 'plain',
            local_currency_code TEXT NOT NULL DEFAULT '$DEFAULT_LOCAL_CURRENCY_CODE',
            balance_fiat_value TEXT NOT NULL DEFAULT '0',
            balance_fiat_updated_at INTEGER,
            is_backed_up INTEGER NOT NULL DEFAULT 0,
            backup_verified_at INTEGER,
            is_imported INTEGER NOT NULL DEFAULT 0,
            is_watch_only INTEGER NOT NULL DEFAULT 0,
            is_active INTEGER NOT NULL DEFAULT 1,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL,
            last_synced_at INTEGER,
            metadata_json TEXT NOT NULL DEFAULT '$EMPTY_JSON'
        )
        """,
        """
        CREATE TABLE $TABLE_WALLET_ASSETS (
            wallet_asset_id TEXT NOT NULL PRIMARY KEY,
            wallet_id TEXT NOT NULL,
            asset_id TEXT NOT NULL,
            network_id TEXT NOT NULL,
            is_enabled INTEGER NOT NULL DEFAULT 1,
            is_visible INTEGER NOT NULL DEFAULT 1,
            balance_raw TEXT NOT NULL DEFAULT '0',
            balance_decimal TEXT NOT NULL DEFAULT '0',
            balance_fiat_value TEXT NOT NULL DEFAULT '0',
            local_currency_code TEXT NOT NULL DEFAULT '$DEFAULT_LOCAL_CURRENCY_CODE',
            price_fiat_value TEXT NOT NULL DEFAULT '0',
            price_fiat_updated_at INTEGER,
            balance_updated_at INTEGER,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL,
            metadata_json TEXT NOT NULL DEFAULT '$EMPTY_JSON',
            UNIQUE(wallet_id, asset_id),
            FOREIGN KEY(wallet_id) REFERENCES $TABLE_WALLETS(wallet_id) ON DELETE CASCADE,
            FOREIGN KEY(asset_id) REFERENCES $TABLE_SUPPORTED_ASSETS(asset_id) ON DELETE RESTRICT,
            FOREIGN KEY(network_id) REFERENCES $TABLE_SUPPORTED_NETWORKS(network_id) ON DELETE RESTRICT
        )
        """,
        """
        CREATE TABLE $TABLE_WALLET_ADDRESSES (
            address_id TEXT NOT NULL PRIMARY KEY,
            wallet_id TEXT NOT NULL,
            network_id TEXT NOT NULL,
            address TEXT NOT NULL,
            address_type TEXT NOT NULL,
            derivation_path TEXT,
            public_key TEXT,
            private_key_id TEXT,
            is_primary INTEGER NOT NULL DEFAULT 0,
            is_change INTEGER NOT NULL DEFAULT 0,
            address_index INTEGER,
            label TEXT,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL,
            last_used_at INTEGER,
            metadata_json TEXT NOT NULL DEFAULT '$EMPTY_JSON',
            UNIQUE(wallet_id, network_id, address),
            FOREIGN KEY(wallet_id) REFERENCES $TABLE_WALLETS(wallet_id) ON DELETE CASCADE,
            FOREIGN KEY(network_id) REFERENCES $TABLE_SUPPORTED_NETWORKS(network_id) ON DELETE RESTRICT
        )
        """,
        """
        CREATE TABLE $TABLE_WALLET_PRIVATE_KEYS (
            private_key_id TEXT NOT NULL PRIMARY KEY,
            wallet_id TEXT NOT NULL,
            network_id TEXT NOT NULL,
            address_id TEXT,
            key_material TEXT NOT NULL,
            key_format TEXT NOT NULL,
            derivation_path TEXT,
            public_key TEXT,
            key_source TEXT NOT NULL,
            is_imported INTEGER NOT NULL DEFAULT 0,
            is_encrypted INTEGER NOT NULL DEFAULT 0,
            key_fingerprint TEXT,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL,
            metadata_json TEXT NOT NULL DEFAULT '$EMPTY_JSON',
            UNIQUE(wallet_id, network_id, derivation_path),
            FOREIGN KEY(wallet_id) REFERENCES $TABLE_WALLETS(wallet_id) ON DELETE CASCADE,
            FOREIGN KEY(network_id) REFERENCES $TABLE_SUPPORTED_NETWORKS(network_id) ON DELETE RESTRICT,
            FOREIGN KEY(address_id) REFERENCES $TABLE_WALLET_ADDRESSES(address_id) ON DELETE SET NULL
        )
        """,
        """
        CREATE TABLE $TABLE_WALLET_TRANSACTIONS (
            transaction_id TEXT NOT NULL PRIMARY KEY,
            wallet_id TEXT NOT NULL,
            asset_id TEXT NOT NULL,
            network_id TEXT NOT NULL,
            transaction_hash TEXT,
            direction TEXT NOT NULL,
            status TEXT NOT NULL,
            amount_raw TEXT NOT NULL,
            amount_decimal TEXT NOT NULL,
            fee_raw TEXT,
            fee_decimal TEXT,
            fee_asset_id TEXT,
            fiat_value TEXT,
            local_currency_code TEXT NOT NULL DEFAULT '$DEFAULT_LOCAL_CURRENCY_CODE',
            from_address TEXT,
            to_address TEXT,
            block_height INTEGER,
            block_hash TEXT,
            confirmations INTEGER NOT NULL DEFAULT 0,
            nonce TEXT,
            memo TEXT,
            timestamp INTEGER NOT NULL,
            first_seen_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL,
            metadata_json TEXT NOT NULL DEFAULT '$EMPTY_JSON',
            UNIQUE(wallet_id, network_id, transaction_hash, asset_id),
            FOREIGN KEY(wallet_id) REFERENCES $TABLE_WALLETS(wallet_id) ON DELETE CASCADE,
            FOREIGN KEY(asset_id) REFERENCES $TABLE_SUPPORTED_ASSETS(asset_id) ON DELETE RESTRICT,
            FOREIGN KEY(network_id) REFERENCES $TABLE_SUPPORTED_NETWORKS(network_id) ON DELETE RESTRICT,
            FOREIGN KEY(fee_asset_id) REFERENCES $TABLE_SUPPORTED_ASSETS(asset_id) ON DELETE RESTRICT
        )
        """,
        """
        CREATE TABLE $TABLE_ASSET_MARKET_DATA (
            symbol TEXT NOT NULL PRIMARY KEY,
            name TEXT NOT NULL,
            coin_gecko_id TEXT,
            local_currency_code TEXT NOT NULL DEFAULT '$DEFAULT_LOCAL_CURRENCY_CODE',
            price_usd TEXT NOT NULL DEFAULT '0',
            price_local TEXT NOT NULL DEFAULT '0',
            market_cap_usd TEXT,
            market_cap_local TEXT,
            volume_24h_usd TEXT,
            volume_24h_local TEXT,
            high_24h_usd TEXT,
            low_24h_usd TEXT,
            price_change_24h_percent TEXT,
            description TEXT,
            homepage_url TEXT,
            provider TEXT NOT NULL,
            chart_7d_json TEXT NOT NULL DEFAULT '[]',
            updated_at INTEGER NOT NULL,
            metadata_json TEXT NOT NULL DEFAULT '$EMPTY_JSON'
        )
        """,
    ).map { it.trimIndent() }

    val indexStatements = listOf(
        "CREATE INDEX index_supported_assets_network_id ON $TABLE_SUPPORTED_ASSETS(network_id)",
        "CREATE INDEX index_address_book_network ON $TABLE_ADDRESS_BOOK(network_id)",
        "CREATE INDEX index_address_book_label ON $TABLE_ADDRESS_BOOK(label)",
        "CREATE INDEX index_wallet_assets_wallet_id ON $TABLE_WALLET_ASSETS(wallet_id)",
        "CREATE INDEX index_wallet_assets_asset_id ON $TABLE_WALLET_ASSETS(asset_id)",
        "CREATE INDEX index_wallet_addresses_wallet_network ON $TABLE_WALLET_ADDRESSES(wallet_id, network_id)",
        "CREATE INDEX index_wallet_private_keys_wallet_network ON $TABLE_WALLET_PRIVATE_KEYS(wallet_id, network_id)",
        "CREATE INDEX index_wallet_transactions_wallet_time ON $TABLE_WALLET_TRANSACTIONS(wallet_id, timestamp)",
        "CREATE INDEX index_wallet_transactions_hash ON $TABLE_WALLET_TRANSACTIONS(transaction_hash)",
        "CREATE INDEX index_wallet_transactions_status ON $TABLE_WALLET_TRANSACTIONS(status)",
        "CREATE INDEX index_asset_market_data_updated_at ON $TABLE_ASSET_MARKET_DATA(updated_at)",
    )
}
