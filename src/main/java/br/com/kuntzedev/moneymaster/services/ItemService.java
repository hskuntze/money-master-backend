package br.com.kuntzedev.moneymaster.services;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.kuntzedev.moneymaster.dtos.ItemDTO;
import br.com.kuntzedev.moneymaster.dtos.ScrapingItemDTO;
import br.com.kuntzedev.moneymaster.entities.Item;
import br.com.kuntzedev.moneymaster.entities.ItemHistory;
import br.com.kuntzedev.moneymaster.entities.ItemPrice;
import br.com.kuntzedev.moneymaster.entities.Wishlist;
import br.com.kuntzedev.moneymaster.repositories.ItemHistoryRepository;
import br.com.kuntzedev.moneymaster.repositories.ItemPriceRepository;
import br.com.kuntzedev.moneymaster.repositories.ItemRepository;
import br.com.kuntzedev.moneymaster.repositories.WishlistRepository;
import br.com.kuntzedev.moneymaster.services.exceptions.ResourceNotFoundException;
import br.com.kuntzedev.moneymaster.services.exceptions.UnprocessableRequestException;
import br.com.kuntzedev.moneymaster.services.scraping.ScrapingMediatorService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Service
public class ItemService {

	@Autowired
	private ItemRepository itemRepository;
	
	@Autowired
	private ItemHistoryRepository itemHistoryRepository;
	
	@Autowired
	private ItemPriceRepository itemPriceRepository;
	
	@Autowired
	private WishlistRepository wishlistRepository;
	
	@Autowired
	private ScrapingMediatorService scrapingMediator;
	
	private final Counter registryCounter;
	
	private static final String RNFE = "Resource not found in the database.";
	private static final String NULL_PARAM = "Null parameter.";
	
	public ItemService(MeterRegistry meterRegistry) {
		registryCounter = Counter.builder("item_register_counter").tag("item_registry", "register")
				.description("The amount of times an item has been registered.").register(meterRegistry);
	}
	
	@Transactional(readOnly = true)
	public Page<ItemDTO> findAll(Pageable pageable) {
		Page<Item> page = itemRepository.findAllItemsWithHistory(pageable);
		return page.map(ItemDTO::new);
	}
	
	@Transactional(readOnly = true)
	public ItemDTO findById(Long id) {
		Item item = itemRepository.findItemWithHistory(id).orElseThrow(() -> new ResourceNotFoundException(RNFE));
		return new ItemDTO(item);
	}
	
	@Transactional
	public ItemDTO insert(Long wishlistId, ItemDTO dto) {
		if(dto != null) {
			Item entity = new Item();
			
			dtoToEntity(entity, dto);
			entity.setPrice(dto.getPrice());
			entity.setWishlist(wishlistRepository.findById(wishlistId).orElseThrow(() -> new ResourceNotFoundException("Wishlist: " + RNFE)));
			entity = itemRepository.save(entity);
			
			ItemHistory history = initializeHistory(entity);
			entity.setItemHistory(history);
			
			return new ItemDTO(entity);
		} else {
			throw new UnprocessableRequestException(NULL_PARAM);
		}
	}
	
	@Transactional
	public ItemDTO insertScrapingProduct(Long wishlistId, ScrapingItemDTO dto) {
		if(dto != null) {
			Item entity = new Item();
			
			entity.setName(dto.getName());
			entity.setImage(dto.getImage());
			entity.setLink(dto.getLink());
			entity.setPrice(dto.getPrice());
			entity.setSourcePlatform(dto.getSourcePlatform());
			entity.setWishlist(wishlistRepository.findById(wishlistId).orElseThrow(() -> new ResourceNotFoundException("Wishlist: " + RNFE)));
			entity = itemRepository.save(entity);
			
			ItemHistory history = initializeHistory(entity);
			entity.setItemHistory(history);
			
			registryCounter.increment(1);
			
			return new ItemDTO(entity);
		} else {
			throw new UnprocessableRequestException(NULL_PARAM);
		}
	}
	
	@Transactional
	public ItemDTO update(Long itemId, ItemDTO dto) {
		if(itemId != null && dto != null) {
			Item entity = itemRepository.findById(itemId).orElseThrow(() -> new ResourceNotFoundException(RNFE));
			
			dtoToEntity(entity, dto);
			
			if(entity.getPrice().compareTo(dto.getPrice()) != 0) {
				entity.setPrice(dto.getPrice());
				
				ItemPrice newItemPrice = new ItemPrice();
				newItemPrice.setDate(LocalDate.now());
				newItemPrice.setPrice(dto.getPrice());
				newItemPrice.setItemHistory(entity.getItemHistory());
				itemPriceRepository.save(newItemPrice);
				
				entity.getItemHistory().getItemPrices().add(newItemPrice);
			}
			
			entity.getItemHistory().calculateFluctuation();
			
			entity = itemRepository.save(entity);
			
			return new ItemDTO(entity);
		} else {
			throw new UnprocessableRequestException(NULL_PARAM);
		}
	}
	
	@Transactional
	public void updateItemWishlist(Long itemId, Long wishlistId) {
		if(itemId != null & wishlistId != null) {
			Item entity = itemRepository.findById(itemId).orElseThrow(() -> new ResourceNotFoundException(RNFE));
			Wishlist wishlist = wishlistRepository.findById(wishlistId).orElseThrow(() -> new ResourceNotFoundException("Wishlist: " + RNFE));
			
			entity.setWishlist(wishlist);
			entity = itemRepository.save(entity);
		} else {
			throw new UnprocessableRequestException(NULL_PARAM);
		}
	}
	
	@Transactional
	public void updateItemBasedOnLink(ItemDTO item) {
		if(item != null) {
			Item entity = itemRepository.findById(item.getId()).orElseThrow(() -> new ResourceNotFoundException(RNFE));
			
			ScrapingItemDTO dto = scrapingMediator.updateItemBasedOnLink(entity, entity.getSourcePlatform());
			ItemDTO product = new ItemDTO();
			
			product.setImage(dto.getImage());
			product.setLink(dto.getLink());
			product.setName(dto.getName());
			product.setSourcePlatform(dto.getSourcePlatform());
			product.setPrice(dto.getPrice());
			
			product.setItemHistory(entity.getItemHistory());
			product.setId(entity.getId());
			
			this.update(item.getId(), product);
		}
	}
	
	public void deleteById(Long id) {
		if(id != null) {
			itemRepository.deleteById(id);
		} else {
			throw new UnprocessableRequestException(NULL_PARAM);
		}
	}
	
	public void deleteAll(List<Item> items) {
		itemRepository.deleteAll(items);
	}

	private void dtoToEntity(Item entity, ItemDTO dto) {
		entity.setImage(dto.getImage());
		entity.setLink(dto.getLink());
		entity.setName(dto.getName());
		entity.setSourcePlatform(dto.getSourcePlatform());
	}
	
	private ItemHistory initializeHistory(Item entity) {
		ItemPrice itemPrice = new ItemPrice(LocalDate.now(), entity.getPrice());
		itemPriceRepository.save(itemPrice);
		
		ItemHistory history = new ItemHistory();
		history.setItem(entity);
		history.setFluctuation(0f);
		history.getItemPrices().add(itemPrice);
		itemHistoryRepository.save(history);
		
		itemPrice.setItemHistory(history);
		itemPriceRepository.save(itemPrice);
		
		return history;
	}
}