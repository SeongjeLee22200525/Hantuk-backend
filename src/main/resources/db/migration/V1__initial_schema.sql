CREATE TABLE app_users (
  id VARCHAR(80) PRIMARY KEY,
  email VARCHAR(254) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  nickname VARCHAR(12) NOT NULL,
  real_name VARCHAR(20) NOT NULL DEFAULT '',
  community_id VARCHAR(80) NOT NULL DEFAULT 'school',
  role VARCHAR(20) NOT NULL DEFAULT 'USER' CHECK (role IN ('USER','ADMIN')),
  usage_alert BOOLEAN NOT NULL DEFAULT TRUE,
  exhausted_alert BOOLEAN NOT NULL DEFAULT TRUE,
  refund_alert BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE auth_sessions (
  token_hash VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(80) NOT NULL REFERENCES app_users(id),
  expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_session_user ON auth_sessions(user_id);
CREATE TABLE communities (
  id VARCHAR(80) PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(500) NOT NULL,
  email_domain VARCHAR(254) NOT NULL DEFAULT '',
  invite_hash VARCHAR(100) NOT NULL DEFAULT ''
);
CREATE TABLE memberships (
  user_id VARCHAR(80) NOT NULL REFERENCES app_users(id),
  community_id VARCHAR(80) NOT NULL REFERENCES communities(id),
  verified_at TIMESTAMP WITH TIME ZONE NOT NULL,
  PRIMARY KEY(user_id, community_id)
);
CREATE TABLE shops (
  id VARCHAR(80) PRIMARY KEY,
  owner_id VARCHAR(80) NOT NULL REFERENCES app_users(id),
  name VARCHAR(100) NOT NULL,
  category VARCHAR(50) NOT NULL,
  emoji VARCHAR(16) NOT NULL,
  address VARCHAR(300) NOT NULL,
  distance VARCHAR(50) NOT NULL,
  description VARCHAR(1000) NOT NULL,
  hours VARCHAR(200) NOT NULL,
  color BIGINT NOT NULL
);
CREATE TABLE shop_menus (
  shop_id VARCHAR(80) NOT NULL REFERENCES shops(id),
  name VARCHAR(100) NOT NULL,
  price BIGINT NOT NULL CHECK(price > 0),
  PRIMARY KEY(shop_id, name)
);
CREATE TABLE benefits (
  id VARCHAR(80) PRIMARY KEY,
  donor_id VARCHAR(80) NOT NULL REFERENCES app_users(id),
  shop_id VARCHAR(80) NOT NULL REFERENCES shops(id),
  community_id VARCHAR(80) NOT NULL REFERENCES communities(id),
  donor VARCHAR(20) NOT NULL,
  message VARCHAR(500) NOT NULL,
  total BIGINT NOT NULL CHECK(total >= 1000),
  remaining BIGINT NOT NULL CHECK(remaining >= 0 AND remaining <= total),
  person_limit BIGINT NOT NULL CHECK(person_limit > 0 AND person_limit <= total),
  status VARCHAR(20) NOT NULL DEFAULT 'active' CHECK(status IN ('active','refund','cancelled')),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
  payment_key VARCHAR(100) NOT NULL,
  UNIQUE(donor_id, payment_key)
);
CREATE INDEX idx_benefit_community ON benefits(community_id, created_at);
CREATE TABLE benefit_menus (
  benefit_id VARCHAR(80) NOT NULL REFERENCES benefits(id),
  name VARCHAR(100) NOT NULL,
  PRIMARY KEY(benefit_id, name)
);
CREATE TABLE use_requests (
  id VARCHAR(80) PRIMARY KEY,
  user_id VARCHAR(80) NOT NULL REFERENCES app_users(id),
  benefit_id VARCHAR(80) NOT NULL REFERENCES benefits(id),
  status VARCHAR(20) NOT NULL CHECK(status IN ('issued','pending','completed','cancelled')),
  menu VARCHAR(100) NOT NULL DEFAULT '',
  order_amount BIGINT NOT NULL DEFAULT 0 CHECK(order_amount >= 0),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_request_user ON use_requests(user_id, status);
CREATE TABLE usages (
  id VARCHAR(80) PRIMARY KEY,
  request_id VARCHAR(80) NOT NULL UNIQUE REFERENCES use_requests(id),
  user_id VARCHAR(80) NOT NULL REFERENCES app_users(id),
  benefit_id VARCHAR(80) NOT NULL REFERENCES benefits(id),
  shop_id VARCHAR(80) NOT NULL REFERENCES shops(id),
  menu VARCHAR(100) NOT NULL,
  order_amount BIGINT NOT NULL CHECK(order_amount > 0),
  applied BIGINT NOT NULL CHECK(applied > 0 AND applied <= order_amount),
  used_at TIMESTAMP WITH TIME ZONE NOT NULL,
  settled_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_usage_user_benefit ON usages(user_id, benefit_id);
CREATE INDEX idx_usage_shop_date ON usages(shop_id, used_at);
CREATE TABLE support_tickets (
  id VARCHAR(80) PRIMARY KEY,
  user_id VARCHAR(80) NOT NULL REFERENCES app_users(id),
  target_id VARCHAR(80) NOT NULL,
  type VARCHAR(30) NOT NULL CHECK(type IN ('취소·환불','문제 신고','정산 문의')),
  reason VARCHAR(1000) NOT NULL,
  evidence VARCHAR(1000) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT '접수' CHECK(status IN ('접수','검토 중','처리 완료')),
  refund_amount BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_ticket_user ON support_tickets(user_id, created_at);
CREATE TABLE notices (
  id VARCHAR(80) PRIMARY KEY,
  user_id VARCHAR(80) NOT NULL REFERENCES app_users(id),
  title VARCHAR(100) NOT NULL,
  body VARCHAR(500) NOT NULL,
  target_id VARCHAR(80) NOT NULL,
  is_read BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_notice_user ON notices(user_id, created_at);
