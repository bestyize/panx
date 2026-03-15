create table if not exists users (
    id bigint primary key auto_increment,
    username varchar(64) not null,
    password_hash varchar(255) not null,
    display_name varchar(128) not null,
    enabled boolean not null default true,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    constraint uk_users_username unique (username)
);

create table if not exists file_nodes (
    id bigint primary key auto_increment,
    parent_id bigint null,
    owner_id bigint not null,
    name varchar(255) not null,
    node_type varchar(16) not null,
    storage_path varchar(512) null,
    content_type varchar(255) null,
    size bigint not null default 0,
    extension varchar(32) null,
    sha256 varchar(64) null,
    deleted boolean not null default false,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    constraint fk_file_nodes_owner foreign key (owner_id) references users (id),
    constraint fk_file_nodes_parent foreign key (parent_id) references file_nodes (id),
    index idx_file_nodes_owner_parent (owner_id, parent_id),
    index idx_file_nodes_owner_name (owner_id, name)
);

create table if not exists share_links (
    id bigint primary key auto_increment,
    file_node_id bigint not null,
    share_token varchar(128) not null,
    extract_code varchar(32) null,
    expires_at timestamp(6) null,
    created_by bigint not null,
    created_at timestamp(6) not null,
    enabled boolean not null default true,
    constraint uk_share_links_token unique (share_token),
    constraint fk_share_links_file foreign key (file_node_id) references file_nodes (id),
    constraint fk_share_links_creator foreign key (created_by) references users (id),
    index idx_share_links_file (file_node_id)
);

create table if not exists share_access_logs (
    id bigint primary key auto_increment,
    share_link_id bigint not null,
    access_ip varchar(64) null,
    user_agent varchar(512) null,
    action varchar(32) not null,
    accessed_at timestamp(6) not null,
    constraint fk_share_access_logs_share foreign key (share_link_id) references share_links (id),
    index idx_share_access_logs_share (share_link_id),
    index idx_share_access_logs_action (action)
);

create table if not exists download_tasks (
    id bigint primary key auto_increment,
    owner_id bigint not null,
    target_parent_id bigint null,
    file_node_id bigint null,
    source_url varchar(2048) not null,
    file_name varchar(255) not null,
    status varchar(32) not null,
    bytes_downloaded bigint not null default 0,
    total_bytes bigint null,
    error_message varchar(1024) null,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    constraint fk_download_tasks_owner foreign key (owner_id) references users (id),
    constraint fk_download_tasks_parent foreign key (target_parent_id) references file_nodes (id),
    constraint fk_download_tasks_file foreign key (file_node_id) references file_nodes (id),
    index idx_download_tasks_owner (owner_id, created_at),
    index idx_download_tasks_status (status)
);
