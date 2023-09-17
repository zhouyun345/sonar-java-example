import java.util.List;
import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class User {

  @NotBlank
  private String name;
}

class Order {

  private Integer id;
}

class Command {

  @NotBlank
  private String guid;

  private Order order; // Compliant

  private User owner; // Noncompliant

  private List<User> members; // Noncompliant [[sc=11;ec=21]] {{Add missing "@Valid" on "members" to validate it with "Bean Validation".}}

  private Building.Size<User> office; // Noncompliant [[sc=11;ec=30]]
  private Building.Size company; // Compliant - Parametrized type, non-specified
}

@RestController
@RequestMapping("/v1/bp")
class CompliantController {

  @PostMapping("/test1")
  public Result<ECardLeadMetaDataResource> test1(
    @RequestBody @Validated User user) { // Compliant

  }

  @PostMapping("/test2")
  public Result<ECardLeadMetaDataResource> test2(
    @RequestBody @Valid User user) { // Compliant

  }

  @PostMapping("/test3")
  public Result<ECardLeadMetaDataResource> test3(
    @RequestBody User user) { // Noncompliant

  }

  @PutMapping("/test4")
  public Result<ECardLeadMetaDataResource> test4(
    @RequestBody @Valid Command command) { // Compliant

  }

  @PutMapping("/test5")
  public Result<ECardLeadMetaDataResource> test5(
    @RequestBody Order order) { // Compliant

  }
}

class CompliantService {
  public void login(User user) { // Compliant
  }

  public List<User> list(Order order) { // Compliant
  }
}

@interface KeepsDoctorAway {
}

@KeepsDoctorAway
class Apple {
}

class Orange {
}

class FoodInventory {
  private List<Apple> apples; // Compliant

  private List<Orange> oranges; // Compliant
}

class Human {
  public void eat(Apple apple) { // Compliant
  }

  public void eat(Orange orange) { // Compliant
  }
}

class Building {
  static class Size<T> { }
}
