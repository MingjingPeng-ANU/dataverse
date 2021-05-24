var personSelector = ".person";
var personInputSelector = "[aria-labelledby='metadata_keyword']";

var versionSelection = undefined;
$(document).ready(function () {
  getData().then((data) => {
    //expandPeople();
    updatePeopleInputs(data);
  });
  console.log("ForCode Test");
});

function expandPeople() {
  //Check each element with class 'person'
  $(personSelector).each(function () {
    var personElement = this;
    //If it hasn't already been processed
    if (!$(personElement).hasClass("expanded")) {
      //Mark it as processed
      $(personElement).addClass("expanded");
      var id = personElement.textContent;
      console.log("id: ", id);
      if (id.startsWith("https://orcid.org/")) {
        id = id.substring(18);
      }
      //Try it as an ORCID (could validate that it has the right form and even that it validates as an ORCID, or can just let the GET fail
      $.ajax({
        type: "GET",
        url: "https://pub.orcid.org/v3.0/" + id + "/person",
        dataType: "json",
        headers: { Accept: "application/json" },
        success: function (person, status) {
          //If found, construct the HTML for display
          var name =
            person.name["family-name"].value +
            ", " +
            person.name["given-names"].value;
          var html =
            "<a href='https://orcid.org/" +
            id +
            "' target=_blank>" +
            name +
            "</a>";
          personElement.innerHTML = html;
          //If email is public, show it using the jquery popover functionality
          if (person.emails.email.length > 0) {
            $(personElement).popover({
              content: person.emails.email[0].email,
              placement: "top",
              template:
                '<div class="popover" role="tooltip" style="max-width:600px;word-break:break-all"><div class="arrow"></div><h3 class="popover-title"></h3><div class="popover-content"></div></div>',
            });
            personElement.onmouseenter = function () {
              $(this).popover("show");
            };
            personElement.onmouseleave = function () {
              $(this).popover("hide");
            };
          }
          //Store the most recent 100 ORCIDs - could cache results, but currently using this just to prioritized recently used ORCIDs in search results
          if (localStorage.length > 100) {
            localStorage.removeItem(localStorage.key(0));
          }
          localStorage.setItem(id, name);
        },
        failure: function (jqXHR, textStatus, errorThrown) {
          //Generic logging - don't need to do anything if 404 (leave display as is)
          if (jqXHR.status != 404) {
            console.error(
              "The following error occurred: " + textStatus,
              errorThrown
            );
          }
        },
      });
    }
  });
}

function getData() {
  console.log(versionSelection);
  return new Promise((resolve, reject) => {
    fetch("http://localhost:3000/api/forCode")
      //
      .then((dd) => dd.status === 201 && dd.json())
      .then((data) => resolve(data))
      .catch((err) => console.log(err));
  });
}

function select2Config(index, data, selectId) {
  if (index % 3 === 0) {
    return $("#" + selectId).select2({
      theme: "bootstrap",
      width: "500px",
      //tags: true,
      delay: 500,
      language: {
        searching: function (params) {
          // Change this to be appropriate for your application
          return "Search by name, email, or ORCID�";
        },
      },
      placeholder: "FoR Code version",
      minimumInputLength: 0,
      allowClear: true,
      data: data.voca,
    });
  } else if (index % 3 === 1) {
    return $("#" + selectId).select2({
      theme: "bootstrap",
      width: "500px",
      //tags: true,
      delay: 500,
      language: {
        searching: function (params) {
          // Change this to be appropriate for your application
          return "Search by name, email, or ORCID�";
        },
      },
      placeholder: "FoR Code terms",
      minimumInputLength: 0,
      allowClear: true,
      data: [],
    });
  }
}
function updatePeopleInputs(data) {
  console.log("91", data);
  var num = 0;
  //For each input element within personInputSelector elements
  $(personInputSelector)
    .find("input")
    .each(function (index, ele) {
    console.log("135", index)
      var personInput = this;
      num = num + 1;
      $(personInput).attr("data-person", num);
      //Hide the actual input and give it a data-person number so we can find it
      if (index % 3 !== 2) {
        $(personInput).hide();
        //$(personInput).attr("data-person", num);
        //Add a select2 element to allow search and provide a list of choices
        var selectId = "FoRcodeAddSelect_" + num;
        $(personInput).after(
          "<select id=" +
            selectId +
            ' class="form-control add-resource select2" tabindex="-1" aria-hidden="true">'
        );
        {
          select2Config(index, data, selectId);
        }
      }

      //If the input has a value already, format it the same way as if it were a new selection
      var id = $(personInput).val();
      console.log("306", num);

      //If the initial value is not an ORCID (legacy, or if tags are enabled), just display it as is
      var newOption = new Option(id, id, true, true);
      $("#" + selectId)
        .append(newOption)
        .trigger("change");

      //Could start with the selection menu open
      //    $("#" + selectId).select2('open');
      //When a selection is made, set the value of the hidden input field
      $("#" + selectId).on("select2:select", function (e) {
        var content = e.params.data;

        const currentItemIndex = parseInt(
          selectId.split("FoRcodeAddSelect_")[1]
        );
        console.log("167", selectId, content.text, currentItemIndex);
        if (currentItemIndex % 3 === 1) {
          const termSelectIndex = `FoRcodeAddSelect_${currentItemIndex + 1}`;
          const urlIndex = currentItemIndex + 2;
          const termIndex = currentItemIndex + 1;
          //console.log(data.term[content.text]);
          $("input[data-person='" + termIndex + "']").val(null);
          $("input[data-person='" + urlIndex + "']").val(null);
          $("#" + termSelectIndex)
            .val(null)
            .trigger("change");
          $("#" + termSelectIndex).html("<option></option>");
          $("#" + termSelectIndex).select2({
            theme: "bootstrap",
            width: "500px",
            //tags: true,
            delay: 500,
            language: {
              searching: function (params) {
                // Change this to be appropriate for your application
                return "Search by name, email, or ORCID�";
              },
            },
            placeholder: "FoR Code terms",
            minimumInputLength: 0,
            allowClear: true,
            data: data.term[content.text],
          });
        }
        console.log("170", currentItemIndex);
        $("input[data-person='" + currentItemIndex + "']").val(content.text);
        if (currentItemIndex % 3 === 2) {
          const urlIndex = currentItemIndex + 1;
          //console.log("206", content.text.split(": ")[2].split("/"));
          let vocaURLArray = content.text.split(": ")[2].split("/");
          //console.log("207", vocaURLArray[6]);
          vocaURLArray[6] = vocaURLArray[6].substring(0, 2);
          console.log("209", vocaURLArray);
          const vocaURL = vocaURLArray.join("/");
          console.log("voca", vocaURL);
          $("input[data-person='" + urlIndex + "']").val(vocaURL);
        }
      });
      //When a selection is cleared, clear the hidden input
      $("#" + selectId).on("select2:clear", function (e) {
        $("input[data-person='" + num + "']").attr("value", "");
      });
    });
}

//Put the text in a result that matches the term in a span with class select2-rendered__match that can be styled (e.g. bold)
function markMatch(text, term) {
  // Find where the match is
  console.log("340", text);
  var match = text.toUpperCase().indexOf(term.toUpperCase());
  var $result = $("<span></span>");
  // If there is no match, move on
  console.log("343", match);
  if (match < 0) {
    return $result.text(text);
  }

  // Put in whatever text is before the match
  $result.text(text.substring(0, match));

  // Mark the match
  var $match = $('<span class="select2-rendered__match"></span>');
  $match.text(text.substring(match, match + term.length));

  // Append the matching text
  $result.append($match);

  // Put in whatever is after the match
  $result.append(text.substring(match + term.length));

  return $result;
}
