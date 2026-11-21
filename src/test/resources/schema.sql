SET FOREIGN_KEY_CHECKS = 0;


CREATE TABLE `comment` (
  `deleted` bit(1) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL,
  `modified_at` datetime(6) NOT NULL,
  `post_id` bigint DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `content` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKs1slvnkuemjsq2kj4h3vhx7i1` (`post_id`),
  KEY `FKqm52p1v3o13hy268he0wcngr5` (`user_id`),
  CONSTRAINT `FKqm52p1v3o13hy268he0wcngr5` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKs1slvnkuemjsq2kj4h3vhx7i1` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `comment_seq` (
  `next_val` bigint DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `likes` (
  `deleted` bit(1) DEFAULT NULL,
  `is_liked` bit(1) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL,
  `modified_at` datetime(6) NOT NULL,
  `post_id` bigint DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKowd6f4s7x9f3w50pvlo6x3b41` (`post_id`),
  KEY `FKnvx9seeqqyy71bij291pwiwrg` (`user_id`),
  CONSTRAINT `FKnvx9seeqqyy71bij291pwiwrg` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKowd6f4s7x9f3w50pvlo6x3b41` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `likes_seq` (
  `next_val` bigint DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `post` (
  `deleted` bit(1) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL,
  `modified_at` datetime(6) NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `content` varchar(255) DEFAULT NULL,
  `title` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK7ky67sgi7k0ayf22652f7763r` (`user_id`),
  CONSTRAINT `FK7ky67sgi7k0ayf22652f7763r` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `post_image` (
  `deleted` bit(1) DEFAULT NULL,
  `display_order` int DEFAULT NULL,
  `is_primary` bit(1) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL,
  `modified_at` datetime(6) NOT NULL,
  `post_id` bigint DEFAULT NULL,
  `image_url` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKp847rtpffsryx3932y7o6ko8y` (`image_url`),
  KEY `FKsip7qv57jw2fw50g97t16nrjr` (`post_id`),
  CONSTRAINT `FKsip7qv57jw2fw50g97t16nrjr` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `post_image_seq` (
  `next_val` bigint DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `post_seq` (
  `next_val` bigint DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `post_view` (
  `created_at` datetime(6) NOT NULL,
  `modified_at` datetime(6) NOT NULL,
  `post_id` bigint NOT NULL,
  `view_count` bigint DEFAULT NULL,
  PRIMARY KEY (`post_id`),
  CONSTRAINT `FKaymjwcor4fcsxhgqgus7302a0` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `profile_image` (
  `deleted` bit(1) DEFAULT NULL,
  `display_order` int DEFAULT NULL,
  `is_primary` bit(1) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL,
  `modified_at` datetime(6) NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `image_url` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKrtt94mhxlet5opmycd257xhrt` (`image_url`),
  KEY `FKsbiasd8poxtjj1pr99oi0rrx5` (`user_id`),
  CONSTRAINT `FKsbiasd8poxtjj1pr99oi0rrx5` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `profile_image_seq` (
  `next_val` bigint DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `refresh_token` (
  `expires_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `token_string` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `refresh_token_seq` (
  `next_val` bigint DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `users` (
  `deleted` bit(1) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL,
  `modified_at` datetime(6) NOT NULL,
  `email` varchar(255) DEFAULT NULL,
  `hashed_password` varchar(255) DEFAULT NULL,
  `nickname` varchar(255) DEFAULT NULL,
  `role` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`),
  UNIQUE KEY `UK2ty1xmrrgtn89xt7kyxx6ta7h` (`nickname`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `users_seq` (
  `next_val` bigint DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


INSERT INTO `comment_seq` VALUES (1);
INSERT INTO `likes_seq` VALUES (1);
INSERT INTO `post_image_seq` VALUES (1);
INSERT INTO `post_seq` VALUES (1);
INSERT INTO `profile_image_seq` VALUES (1);
INSERT INTO `refresh_token_seq` VALUES (1);
INSERT INTO `users_seq` VALUES (1);

SET FOREIGN_KEY_CHECKS = 1;
