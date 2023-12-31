package br.com.kuntzedev.moneymaster.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import br.com.kuntzedev.moneymaster.dtos.TotalExpenseByMonthBasicDTO;
import br.com.kuntzedev.moneymaster.entities.TotalExpenseByMonth;

@Repository
public interface TotalExpenseByMonthRepository extends JpaRepository<TotalExpenseByMonth, Long> {
	
	@Query("SELECT COUNT(tbem) FROM TotalExpenseByMonth tbem "
			+ "WHERE tbem.expenseTrack.user.id = :userId "
			+ "AND EXTRACT(MONTH FROM tbem.date) = :month")
	int verifyIfTotalExpenseByMonthExists(Long userId, int month);
	
	@Query("SELECT tbem FROM TotalExpenseByMonth tbem "
			+ "WHERE tbem.expenseTrack.user.id = :userId "
			+ "AND EXTRACT(MONTH FROM tbem.date) = :month "
			+ "AND EXTRACT(YEAR FROM tbem.date) = :year")
	Optional<TotalExpenseByMonth> findByMonth(Long userId, int month, int year);
	
	@Query("SELECT tbem FROM TotalExpenseByMonth tbem "
			+ "WHERE tbem.expenseTrack.user.id = :userId")
	Page<TotalExpenseByMonth> findByUser(Pageable pageable, Long userId);
	
	@Query("SELECT new br.com.kuntzedev.moneymaster.dtos.TotalExpenseByMonthBasicDTO(obj.date, obj.totalExpended) "
			+ "FROM TotalExpenseByMonth obj")
	List<TotalExpenseByMonthBasicDTO> getBasicData();
}