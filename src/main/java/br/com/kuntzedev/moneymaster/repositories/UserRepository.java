package br.com.kuntzedev.moneymaster.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import br.com.kuntzedev.moneymaster.entities.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long>{

	User findByEmail(String email);
	
	@Query(nativeQuery = true, value = "SELECT * FROM tb_user u "
			+ " INNER JOIN tb_recovery_token rt ON u.id = rt.user_id "
			+ " WHERE rt.token = :token")
	User findByRecoveryToken(String token);
	
	@Query("SELECT u FROM User u "
			+ "LEFT JOIN FETCH u.wishlists w "
			+ "WHERE u.id = :id")
	Optional<User> findUser(Long id);
}