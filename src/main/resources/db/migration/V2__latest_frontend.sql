-- Separate v2 data preserves the original prototype's existing accounts/ledger.
CREATE TABLE prototype_accounts (
  id VARCHAR(20) PRIMARY KEY,
  password_hash VARCHAR(100) NOT NULL,
  name VARCHAR(50) NOT NULL,
  birth_date DATE NOT NULL,
  email VARCHAR(254) NOT NULL UNIQUE,
  phone_number VARCHAR(20) NOT NULL,
  role VARCHAR(20) NOT NULL DEFAULT 'USER',
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE prototype_sessions (
  token_hash VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(20) NOT NULL REFERENCES prototype_accounts(id),
  expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_proto_session_user ON prototype_sessions(user_id);
CREATE TABLE prototype_state (
  id INTEGER PRIMARY KEY CHECK(id=1),
  revision BIGINT NOT NULL,
  document TEXT NOT NULL
);
CREATE TABLE prototype_commands (
  user_id VARCHAR(20) NOT NULL REFERENCES prototype_accounts(id),
  command_id VARCHAR(80) NOT NULL,
  fingerprint VARCHAR(64) NOT NULL,
  result_json TEXT NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  PRIMARY KEY(user_id,command_id)
);
CREATE TABLE prototype_email_challenges (
  id VARCHAR(80) PRIMARY KEY,
  email VARCHAR(254) NOT NULL,
  code_hash VARCHAR(100) NOT NULL,
  expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
  verified BOOLEAN NOT NULL DEFAULT FALSE,
  consumed BOOLEAN NOT NULL DEFAULT FALSE,
  attempts INTEGER NOT NULL DEFAULT 0
);
